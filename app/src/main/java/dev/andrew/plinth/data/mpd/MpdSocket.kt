package dev.andrew.plinth.data.mpd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * One MPD command connection. Reads are bounded by the socket's own `soTimeout`
 * so a stalled server costs a [MpdException.Timeout], never a hung coroutine, and
 * replies are read to their terminator rather than by scanning for `"OK"`.
 */
class MpdSocket(
    private val factory: (host: String, port: Int) -> Socket = { host, port ->
        Socket().apply { connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
    },
) {

    private val mutex = Mutex()
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    val isOpen: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed } == true

    suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        val s = factory(host, port)
        s.soTimeout = CONNECT_TIMEOUT_MS
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
        readLine() // "OK MPD <protocol version>" — the protocol version, not the daemon's
        Unit
    }

    suspend fun command(line: String, timeoutMs: Int = STATE_TIMEOUT_MS): MpdReply =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                arm(timeoutMs)
                writeLine(line)
                MpdParser.parse(readReplyText())
            }
        }

    suspend fun commandBinary(line: String, timeoutMs: Int): BinaryReply =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                arm(timeoutMs)
                writeLine(line)

                val first = readLine()
                if (first.startsWith("ACK")) throw ackAsException(first)

                var size = 0
                var count = 0
                for (header in listOf(first, readLine())) {
                    when {
                        header.startsWith(SIZE) -> size = header.removePrefix(SIZE).trim().toInt()
                        header.startsWith(BINARY) -> count = header.removePrefix(BINARY).trim().toInt()
                    }
                }
                val bytes = readExactly(count)
                val terminator = readLine()
                if (terminator != "OK") throw MpdException.Protocol("unexpected terminator: $terminator")
                BinaryReply(size, bytes)
            }
        }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    private fun arm(timeoutMs: Int) {
        val s = socket ?: throw MpdException.Disconnected("not connected")
        s.soTimeout = timeoutMs
    }

    private fun writeLine(line: String) {
        val out = output ?: throw MpdException.Disconnected("not connected")
        out.write((line + "\n").toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** Reads key/value or ACK lines until the `OK` terminator. */
    private fun readReplyText(): String {
        val sb = StringBuilder()
        while (true) {
            val line = readLine()
            sb.append(line).append('\n')
            if (line == "OK") break
            if (line.startsWith("ACK")) break
        }
        return sb.toString()
    }

    private fun readLine(): String {
        val inp = input ?: throw MpdException.Disconnected("not connected")
        val sb = StringBuilder()
        while (true) {
            val b = try {
                inp.read()
            } catch (_: SocketTimeoutException) {
                throw MpdException.Timeout()
            }
            if (b == -1) throw MpdException.Disconnected()
            if (b == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(b.toChar())
        }
    }

    private fun readExactly(count: Int): ByteArray {
        val inp = input ?: throw MpdException.Disconnected("not connected")
        val bytes = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = try {
                inp.read(bytes, read, count - read)
            } catch (_: SocketTimeoutException) {
                throw MpdException.Timeout()
            }
            if (n == -1) throw MpdException.Disconnected()
            read += n
        }
        return bytes
    }

    private fun ackAsException(line: String): MpdException {
        val ack = MpdParser.parse(line) as? MpdReply.Ack
        return MpdException.Protocol(ack?.message ?: line)
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
        const val STATE_TIMEOUT_MS = 3_000
        private const val SIZE = "size: "
        private const val BINARY = "binary: "
    }
}
