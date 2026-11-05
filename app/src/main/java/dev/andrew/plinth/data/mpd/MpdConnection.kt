package dev.andrew.plinth.data.mpd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Up : ConnectionState

    /** Not connected yet; [failuresAgo] counters attempts so the UI can say "retrying". */
    data class Connecting(val failuresAgo: Int) : ConnectionState

    data class Unavailable(val message: String) : ConnectionState
}

/**
 * Owns the sockets for the life of the app: connects, parks one in `idle`, and
 * rebuilds everything with backoff when anything goes wrong. Downstream sees only
 * a [StateFlow], the idle [events], and [withClient] — never a raw socket.
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

    /** Subsystems MPD reported as changed, in order. Repositories refresh off this. */
    private val _events = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)
    val events: SharedFlow<Set<String>> = _events.asSharedFlow()

    @Volatile
    private var socket: MpdSocket? = null

    @Volatile
    private var idleSocket: MpdSocket? = null

    @Volatile
    private var client: MpdClient? = null

    @Volatile
    private var stopped: Boolean = false

    @Volatile
    var failures: Int = 0
        private set

    val isUp: Boolean get() = _state.value is ConnectionState.Up

    /**
     * Runs until [close]. Two sockets, deliberately: MPD closes a connection that
     * sends a command while parked in `idle`, so transport presses need their own.
     */
    suspend fun run() {
        // Cancelling the owning scope cannot interrupt a blocking socket read, so the
        // watchdog closes the sockets the moment the job starts dying — that read then
        // throws and the loop unwinds instead of leaking a thread and two fds.
        val self = currentCoroutineContext()[Job]
        val watchdog = self?.let { job ->
            CoroutineScope(Dispatchers.IO).launch {
                job.join()
                closeQuietly()
            }
        }
        try {
            while (!stopped && currentCoroutineContext().isActive) {
                try {
                    connectBoth()
                    failures = 0
                    _state.value = ConnectionState.Up

                    val idle = idleFactory(idleSocket!!)
                    while (!stopped && currentCoroutineContext().isActive) {
                        val changed = idle.awaitChange()
                        if (changed.isNotEmpty()) _events.tryEmit(changed)
                    }
                } catch (c: CancellationException) {
                    closeQuietly()
                    throw c
                } catch (t: Throwable) {
                    closeQuietly()
                    if (stopped || !currentCoroutineContext().isActive) break
                    failures++
                    _state.value = if (failures >= UNREACHABLE_AFTER) {
                        ConnectionState.Unavailable(t.message ?: t::class.simpleName ?: "unreachable")
                    } else {
                        ConnectionState.Connecting(failures)
                    }
                    sleep(backoff.next(failures))
                }
            }
        } finally {
            closeQuietly()
            watchdog?.cancel()
        }
    }

    /** Publishes both sockets only once both are up, so a half-connect leaks nothing. */
    private suspend fun connectBoth() {
        val cmd = socketFactory()
        try {
            cmd.connect(host, port)
        } catch (t: Throwable) {
            cmd.close()
            throw t
        }
        val idle = socketFactory()
        try {
            idle.connect(host, port)
        } catch (t: Throwable) {
            idle.close()
            cmd.close()
            throw t
        }
        socket = cmd
        idleSocket = idle
        client = MpdClient(cmd)
    }

    /** Stops the loop for good and frees both sockets, including a parked idle read. */
    fun close() {
        stopped = true
        closeQuietly()
    }

    /**
     * Runs [block] against the live client. Any failure demotes the link: a command
     * socket that died silently (Wi-Fi roam, NAT timeout) must not leave the UI
     * showing "connected" forever.
     */
    suspend fun <T> withClient(block: suspend (MpdClient) -> T): T {
        val c = client ?: throw MpdException.Disconnected("not connected")
        return try {
            block(c)
        } catch (e: MpdException.Ack) {
            // An ACK is a whole reply: MPD is healthy and the stream is still in step.
            // Demoting here turned every album without a cover into a reconnect.
            throw e
        } catch (e: MpdException) {
            demote(e)
            throw e
        } catch (e: java.io.IOException) {
            demote(e)
            throw MpdException.Disconnected(e.message ?: "read failed")
        }
    }

    private fun demote(cause: Throwable) {
        closeQuietly() // closing the idle socket wakes run(), which reconnects
        if (!stopped) {
            _state.value = ConnectionState.Connecting(failures.coerceAtLeast(1))
        }
    }

    private fun closeQuietly() {
        socket?.close()
        idleSocket?.close()
        socket = null
        idleSocket = null
        client = null
    }

    companion object {
        const val UNREACHABLE_AFTER = 3
    }
}
