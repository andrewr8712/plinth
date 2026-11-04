package dev.andrew.plinth.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.andrew.plinth.AppContainer
import dev.andrew.plinth.data.mpd.ConnectionState
import dev.andrew.plinth.data.mpd.MpdConnection
import dev.andrew.plinth.data.model.PlayerState
import dev.andrew.plinth.data.model.PlaybackState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything the now-playing surface draws, flattened so the UI has no data types. */
data class PlayerUi(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
    val uri: String = "",
    val elapsed: Double? = null,
    val duration: Double? = null,
    val isPlaying: Boolean = false,
    val connected: Boolean = false,
    val formatLine: String? = null,
    val bitrateKbps: Int? = null,
    val stateLabel: String = "Connecting…",
    val queueCount: Int = 0,
)

class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private var connection: MpdConnection? = null
    private var pollJob: Job? = null
    private var lastSongId: Int? = null
    private var lastQueueVersion: Int = Int.MIN_VALUE

    private val _queue = MutableStateFlow<List<dev.andrew.plinth.data.model.QueueItem>>(emptyList())
    val queue: StateFlow<List<dev.andrew.plinth.data.model.QueueItem>> = _queue.asStateFlow()

    private val _art = MutableStateFlow<dev.andrew.plinth.data.art.ArtRepository?>(null)
    val art: StateFlow<dev.andrew.plinth.data.art.ArtRepository?> = _art.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = container.settings.flow.first()
            connect(settings.host, settings.port)
        }
    }

    fun connect(host: String, port: Int) {
        pollJob?.cancel()
        connection?.close()
        val conn = container.connection(host, port)
        connection = conn
        viewModelScope.launch { conn.run() }
        viewModelScope.launch {
            conn.state.collect { state ->
                if (state is ConnectionState.Up) {
                    _art.value = runCatching { conn.withClient { container.artFor(it) } }.getOrNull()
                } else {
                    _art.value = null
                }
                _ui.value = _ui.value.copy(
                    connected = state is ConnectionState.Up,
                    stateLabel = when (state) {
                        is ConnectionState.Up -> "Connected"
                        is ConnectionState.Connecting -> "Connecting…"
                        is ConnectionState.Unavailable -> state.message
                    },
                )
            }
        }
        pollJob = viewModelScope.launch {
            while (isActive) {
                if (conn.isUp) refresh(conn)
                delay(if (conn.isUp) POLL_MS else RETRY_MS)
            }
        }
    }

    private suspend fun refresh(conn: MpdConnection) {
        try {
            val status = conn.withClient { it.status() }
            val song = if (status.songId != lastSongId) {
                lastSongId = status.songId
                conn.withClient { it.currentSong() }
            } else {
                null
            }
            _ui.value = _ui.value.merge(status, song)
            if (status.queueVersion != lastQueueVersion) {
                lastQueueVersion = status.queueVersion
                runCatching { _queue.value = conn.withClient { it.playlistInfo() } }
            }
        } catch (_: Exception) {
            // the connection supervisor owns recovery; the next tick tries again
        }
    }

    private fun PlayerUi.merge(status: PlayerState, song: dev.andrew.plinth.data.model.Track?): PlayerUi {
        val base = if (song != null) {
            copy(
                title = song.title.ifBlank { song.uri.substringAfterLast('/') },
                artist = song.artist.ifBlank { song.albumArtist },
                album = song.album,
                albumId = song.albumId,
                uri = song.uri,
            )
        } else {
            this
        }
        val format = status.format
        return base.copy(
            elapsed = status.elapsed ?: base.elapsed,
            duration = status.duration ?: base.duration,
            isPlaying = status.state == PlaybackState.Play,
            formatLine = format?.let { f ->
                if (f.isDsd) {
                    "DSD${f.dsdRate} · ${f.channels} ch"
                } else {
                    "%.1f kHz · %d bit · %d ch".format(f.sampleRate / 1000.0, f.bits, f.channels)
                }
            },
            bitrateKbps = status.bitrateKbps,
            stateLabel = when (status.state) {
                PlaybackState.Play -> "Playing"
                PlaybackState.Pause -> "Paused"
                PlaybackState.Stop -> "Stopped"
                PlaybackState.Unknown -> base.stateLabel
            },
        )
    }

    fun playPause() = command {
        val playing = _ui.value.isPlaying
        if (playing) it.pause(true) else it.play()
    }

    fun next() = command { it.next() }

    fun previous() = command { it.previous() }

    fun seek(seconds: Double) = command { it.seekCur(seconds) }

    private fun command(block: suspend (dev.andrew.plinth.data.mpd.MpdClient) -> Unit) {
        val conn = connection ?: return
        viewModelScope.launch {
            runCatching { conn.withClient { block(it) } }
            // draw the new state immediately rather than waiting for the next tick
            runCatching { refresh(conn) }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        connection?.close()
    }

    private companion object {
        const val POLL_MS = 500L
        const val RETRY_MS = 2_000L
    }
}

@Suppress("UNCHECKED_CAST")
class PlayerViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlayerViewModel(container) as T
}
