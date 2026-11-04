package dev.andrew.plinth.data.mpd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * One MPD command connection.
 *
 * Two rules keep this honest:
 *  - bytes are decoded as UTF-8, never as Latin-1 (real tags are full of `é` and `’`);
 *  - any failed read is fatal to the connection. MPD has no request ids, so a reply
 *    abandoned half-read cannot be resynchronised — the socket is closed and the
 *    supervisor reconnects instead of parsing the next reply's bytes as this one's.
 */
class MpdSocket(
    private val factory: (host: String, port: Int) -> Socket = { host, port ->
        Socket().apply {
            connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            keepAlive = true
        }
    },
) {

    private val mutex = Mutex()
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    /** MPD's protocol version from the banner (`0.23.5` here), needed for capability checks. */
    var protocolVersion: String? = null
        private set

    val isOpen: Boolean
        get() = socket?.let { !it.isClosed } == true

    suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        val s = factory(host, port)
        s.soTimeout = CONNECT_TIMEOUT_MS
        s.keepAlive = true
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
        try {
            val banner = readLine()
            if (!banner.startsWith(BANNER)) {
                throw MpdException.Protocol("not an MPD server: \"$banner\"")
            }
            protocolVersion = banner.removePrefix(BANNER).trim()
        } catch (t: Throwable) {
            close()
            throw t
        }
        Unit
    }

    suspend fun command(line: String, timeoutMs: Int = STATE_TIMEOUT_MS): MpdReply =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                arm(timeoutMs)
                attempt {
                    writeLine(line)
                    MpdParser.parse(readReplyText())
                }
            }
        }

    /**
     * Header lines, then one or more `binary:` chunks, then `OK`. A bare `OK` means
     * the server had nothing to send (MPD's `readpicture` on a folder-art album).
     */
    suspend fun commandBinary(line: String, timeoutMs: Int = ART_TIMEOUT_MS): BinaryReply =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                arm(timeoutMs)
                attempt {
                    writeLine(line)
                    readBinaryReply()
                }
            }
        }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    private inline fun <T> attempt(block: () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            // The reply may be half-read: nothing can be trusted on this stream again.
            close()
            throw when (t) {
                is MpdException -> t
                is SocketTimeoutException -> MpdException.Timeout()
                is IOException -> MpdException.Disconnected(t.message ?: "read failed")
                else -> t
            }
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
            if (line.startsWith(ACK_PREFIX)) break
        }
        return sb.toString()
    }

    private fun readBinaryReply(): BinaryReply {
        var size = 0
        val body = ByteArrayOutputStream()
        while (true) {
            val line = readLine()
            when {
                line == "OK" -> return BinaryReply(size, body.toByteArray())
                line.startsWith(ACK_PREFIX) -> throw ackException(line)
                line.startsWith(SIZE) -> size = line.removePrefix(SIZE).trim().toIntOrNull() ?: 0
                line.startsWith(BINARY) -> {
                    val n = line.removePrefix(BINARY).trim().toIntOrNull()
                        ?: throw MpdException.Protocol("bad chunk length: $line")
                    body.write(readExactly(n))
                    consumeOptionalNewline()
                }
                else -> throw MpdException.Protocol("unexpected reply line: $line")
            }
        }
    }

    /** Real MPD ends a binary block with a newline; tolerate both shapes. */
    private fun consumeOptionalNewline() {
        val inp = input ?: return
        inp.mark(1)
        if (inp.read() != '\n'.code) inp.reset()
    }

    private fun ackException(line: String): MpdException.Ack {
        val ack = MpdParser.parse(line) as? MpdReply.Ack
            ?: return MpdException.Ack(0, "", line)
        return MpdException.Ack(ack.code, ack.command, ack.message)
    }

    /** UTF-8 on the wire, so assemble bytes — `Int.toChar()` would decode Latin-1. */
    private fun readLine(): String {
        val inp = input ?: throw MpdException.Disconnected("not connected")
        val buf = ByteArrayOutputStream(96)
        while (true) {
            val b = inp.read()
            if (b == -1) throw MpdException.Disconnected()
            if (b == '\n'.code) return buf.toString("UTF-8").removeSuffix("\r")
            buf.write(b)
        }
    }

    private fun readExactly(count: Int): ByteArray {
        val inp = input ?: throw MpdException.Disconnected("not connected")
        val bytes = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = inp.read(bytes, read, count - read)
            if (n == -1) throw MpdException.Disconnected()
            read += n
        }
        return bytes
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
        const val STATE_TIMEOUT_MS = 3_000
        const val ART_TIMEOUT_MS = 15_000
        const val LIST_TIMEOUT_MS = 20_000
        private const val BANNER = "OK MPD "
        private const val ACK_PREFIX = "ACK ["
        private const val SIZE = "size: "
        private const val BINARY = "binary: "
    }
}
