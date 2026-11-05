package dev.andrew.plinth.data.playback

import dev.andrew.plinth.data.model.PlaybackState
import dev.andrew.plinth.data.model.PlayerState
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.data.mpd.ConnectionState
import dev.andrew.plinth.data.mpd.MpdClient
import dev.andrew.plinth.data.mpd.MpdConnection
import dev.andrew.plinth.data.mpd.MpdException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The one owner of the MPD connection.
 *
 * This used to live in the view model, which dies with its activity — fine while the
 * screen was the only consumer, wrong for a playback service that has to keep
 * polling with the screen off. The connection now lives in application scope and
 * runs while at least one owner needs it ([OWNER_UI], [OWNER_SERVICE]); when the
 * last owner lets go the sockets are released, which is the design's "backgrounded
 * with no service → reconnect suspended" rule.
 *
 * Nothing here knows about Android: the service supplies the demand, this supplies
 * the state.
 */
class PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Connecting(failuresAgo = 0))
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _player = MutableStateFlow<PlayerState?>(null)
    val player: StateFlow<PlayerState?> = _player.asStateFlow()

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private var host: String? = null
    private var port: Int = 0
    private val owners = mutableSetOf<String>()
    @Volatile
    private var conn: MpdConnection? = null
    private var runJob: Job? = null

    @Volatile
    private var lastSongId: Int? = null

    /** Commands refresh while the loop polls; two polls interleaving can publish backwards. */
    private val pollMutex = Mutex()

    /** Bumped on every start/stop so a dying job cannot publish into the next one. */
    private var generation = 0

    /** False until someone supplies an endpoint; the service uses this after a restart. */
    val isConfigured: Boolean
        get() = synchronized(lock) { host != null }

    /** Remembers [host]/[port] and restarts only when the endpoint actually changed. */
    fun connect(host: String, port: Int) {
        synchronized(lock) {
            val same = this.host == host && this.port == port
            this.host = host
            this.port = port
            if (!same) {
                stopLocked()
                if (owners.isNotEmpty()) startLocked()
            }
        }
    }

    fun setActive(owner: String, active: Boolean) {
        synchronized(lock) {
            val changed = if (active) owners.add(owner) else owners.remove(owner)
            if (!changed) return
            if (owners.isEmpty()) stopLocked() else startLocked()
        }
    }

    /** Drops the current backoff wait and redials now. What the chip's button calls. */
    fun retry() {
        synchronized(lock) {
            if (owners.isEmpty()) return
            stopLocked()
            startLocked()
        }
    }

    suspend fun <T> withClient(block: suspend (MpdClient) -> T): T {
        val c = conn ?: throw MpdException.Disconnected("not connected")
        return c.withClient(block)
    }

    /** One immediate `status`/`currentSong`, for a command that must not wait a tick. */
    suspend fun refresh() {
        val c = conn ?: return
        if (c.isUp) poll(c)
    }

    /**
     * Bare `pause` toggles against the server's real state; deriving the direction
     * from the last poll can send "resume" to a streamer another client just paused.
     */
    fun playPause() = command {
        when (_player.value?.state) {
            PlaybackState.Play, PlaybackState.Pause -> it.pause()
            else -> it.play()
        }
    }

    fun play() = command { it.play() }

    fun pause() = command { it.pause(true) }

    fun stop() = command { it.stop() }

    fun next() = command { it.next() }

    fun previous() = command { it.previous() }

    fun seek(seconds: Double) = command { it.seekCur(seconds) }

    private fun command(block: suspend (MpdClient) -> Unit) {
        scope.launch {
            runCatching { withClient { block(it) } }
            runCatching { refresh() }
        }
    }

    private fun startLocked() {
        val h = host ?: return
        if (runJob?.isActive == true) return
        generation++
        val gen = generation
        val c = MpdConnection(h, port)
        conn = c
        _connection.value = ConnectionState.Connecting(failuresAgo = 0)
        runJob = scope.launch {
            launch { c.run() }
            launch {
                c.state.collect { state ->
                    if (gen != generation) return@collect
                    _connection.value = state
                    // A fresh link may be on a different song: force a song read.
                    if (state is ConnectionState.Up) lastSongId = Int.MIN_VALUE
                }
            }
            while (isActive) {
                if (c.isUp) poll(c)
                delay(if (c.isUp) POLL_MS else RETRY_MS)
            }
        }
    }

    private fun stopLocked() {
        generation++
        runJob?.cancel()
        runJob = null
        conn?.close()
        conn = null
        _connection.value = ConnectionState.Connecting(failuresAgo = 0)
    }

    private suspend fun poll(c: MpdConnection) = pollMutex.withLock {
        if (conn !== c) return@withLock
        try {
            val status = c.withClient { it.status() }
            // Read the song before publishing the status, so a collector that sees a
            // new song id can rely on the track already being there.
            when {
                // MPD has no current song (queue cleared, or stopped from empty): the
                // old title must not outlive it on the screen or in the notification.
                status.songId == null -> {
                    lastSongId = null
                    _track.value = null
                }
                status.songId != lastSongId || _track.value == null -> {
                    lastSongId = status.songId
                    _track.value = c.withClient { it.currentSong() }
                }
            }
            // A stop or retry replaced the link while the reads were in flight.
            if (conn !== c) return@withLock
            _player.value = status
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The connection supervisor owns recovery; the next tick tries again.
        }
    }

    companion object {
        const val OWNER_UI = "ui"
        const val OWNER_SERVICE = "service"

        private const val POLL_MS = 1_000L
        private const val RETRY_MS = 1_000L
    }
}
