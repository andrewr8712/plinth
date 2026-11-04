package dev.andrew.plinth.data.mpd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive

sealed interface ConnectionState {
    data object Up : ConnectionState

    /** Not connected yet; [failuresAgo] counters attempts so the UI can say "retrying". */
    data class Connecting(val failuresAgo: Int) : ConnectionState

    data class Unavailable(val message: String) : ConnectionState
}

/**
 * Owns the socket for the life of the app: connects, parks in `idle`, and rebuilds
 * the connection with backoff when anything goes wrong. Nothing downstream ever
 * sees a raw socket — only a [StateFlow] and [withClient], which fails fast when
 * the link is down rather than blocking the caller.
 */
class MpdConnection(
    private val host: String,
    private val port: Int,
    private val socketFactory: () -> MpdSocket = { MpdSocket() },
    private val idleFactory: (MpdSocket) -> IdleWaiter = { MpdIdle(it) },
    private val backoff: Backoff = Backoff(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting(0))
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile
    private var socket: MpdSocket? = null

    @Volatile
    private var client: MpdClient? = null

    @Volatile
    var failures: Int = 0
        private set

    val isUp: Boolean get() = _state.value is ConnectionState.Up

    /** Runs forever: connect → idle → on any failure, back off and start again. */
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            try {
                val s = socketFactory()
                s.connect(host, port)
                socket = s
                client = MpdClient(s)
                failures = 0
                _state.value = ConnectionState.Up

                val idle = idleFactory(s)
                while (currentCoroutineContext().isActive) {
                    idle.awaitChange()
                    // Task 5 keeps the link warm; the repositories subscribe in M6.
                }
            } catch (c: CancellationException) {
                closeQuietly()
                throw c
            } catch (t: Throwable) {
                closeQuietly()
                failures++
                _state.value = if (failures >= UNREACHABLE_AFTER) {
                    ConnectionState.Unavailable(t.message ?: t::class.simpleName ?: "unreachable")
                } else {
                    ConnectionState.Connecting(failures)
                }
                sleep(backoff.next(failures))
            }
        }
    }

    suspend fun <T> withClient(block: suspend (MpdClient) -> T): T {
        val c = client ?: throw MpdException.Disconnected("not connected")
        return block(c)
    }

    private fun closeQuietly() {
        socket?.close()
        socket = null
        client = null
    }

    companion object {
        const val UNREACHABLE_AFTER = 3
    }
}
