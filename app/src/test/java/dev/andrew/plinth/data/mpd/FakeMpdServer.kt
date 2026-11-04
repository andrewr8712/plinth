package dev.andrew.plinth.data.mpd

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * A scriptable stand-in for MPD. Each connection gets the banner, then every line
 * is matched against the registered handlers by prefix. `stall` accepts and reads
 * without ever replying — what a stale SMB mount looks like on the wire.
 */
class FakeMpdServer : Closeable {

    private val server = ServerSocket(0)
    private val handlers = Collections.synchronizedMap(LinkedHashMap<String, (String) -> String?>())
    private val last = AtomicReference<String?>(null)

    @Volatile
    var stall: Boolean = false

    val port: Int get() = server.localPort
    val lastCommand: String? get() = last.get()

    fun start(): FakeMpdServer {
        thread(isDaemon = true, name = "fake-mpd-accept") {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    return@thread
                }
                thread(isDaemon = true, name = "fake-mpd-conn") { serve(client) }
            }
        }
        return this
    }

    fun onCommand(prefix: String, handler: (String) -> String?) {
        handlers[prefix] = handler
    }

    private fun serve(client: Socket) {
        client.use { c ->
            val out: OutputStream = c.getOutputStream()
            val input: InputStream = c.getInputStream()
            out.write("OK MPD 0.23.5\n".toByteArray())
            out.flush()
            while (true) {
                val line = readLineFrom(input) ?: return
                last.set(line)
                if (stall) continue
                val handler = handlers.entries.firstOrNull { line.startsWith(it.key) }?.value
                if (handler == null) {
                    out.write("OK\n".toByteArray())
                    out.flush()
                    continue
                }
                val response = handler(line) ?: continue // null = never answers (idle)
                out.write(response.toByteArray())
                out.flush()
            }
        }
    }

    private fun readLineFrom(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(b.toChar())
        }
    }

    override fun close() {
        runCatching { server.close() }
    }
}
