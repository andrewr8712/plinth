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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
    val songId: Int? = null,
    val strip: dev.andrew.plinth.data.model.StripCells? = null,
    /** The envelope frame for the position being heard, or null when there is none. */
    val level: dev.andrew.plinth.data.loudness.StereoLevel? = null,
    val isDsd: Boolean = false,
)

class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private var connection: MpdConnection? = null
    private var pollJob: Job? = null
    private var lastSongId: Int? = null
    private var lastQueueVersion: Int = Int.MIN_VALUE
    private var lastOutput: dev.andrew.plinth.data.model.OutputInfo? = null
    private var lastTrack: dev.andrew.plinth.data.model.Track? = null

    private val _queue = MutableStateFlow<List<dev.andrew.plinth.data.model.QueueItem>>(emptyList())
    val queue: StateFlow<List<dev.andrew.plinth.data.model.QueueItem>> = _queue.asStateFlow()

    private var librarySynced = false

    private val _syncingLibrary = MutableStateFlow(false)
    val syncingLibrary: StateFlow<Boolean> = _syncingLibrary.asStateFlow()

    /** Straight from Room, so the grid draws from the cache rather than the network. */
    val albums: StateFlow<List<dev.andrew.plinth.data.model.Album>> = container.library.albums()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<String>> = container.library.artists()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<String>> = container.library.folders()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    val tracks: StateFlow<List<dev.andrew.plinth.data.model.Track>> = container.library.tracks()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** Search runs in Room on a prefix index-free scan; 1,866 rows is instant. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<dev.andrew.plinth.data.model.Track>> = _query
        .flatMapLatest { container.library.search(it) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    fun setQuery(text: String) {
        _query.value = text
    }

    private val _output = MutableStateFlow<dev.andrew.plinth.data.model.OutputInfo?>(null)
    val output: StateFlow<dev.andrew.plinth.data.model.OutputInfo?> = _output.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting(failuresAgo = 0))
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val settings: StateFlow<dev.andrew.plinth.data.settings.Settings> =
        container.settings.flow.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            dev.andrew.plinth.data.settings.Settings(),
        )

    private val _art = MutableStateFlow<dev.andrew.plinth.data.art.ArtRepository?>(null)
    val art: StateFlow<dev.andrew.plinth.data.art.ArtRepository?> = _art.asStateFlow()

    /** Opened once per session; null until a loudness file has been imported. */
    private var loudness: dev.andrew.plinth.data.loudness.LoudnessIndex? = null
    private var levelJob: Job? = null
    private var lastElapsed = 0.0
    private var lastElapsedAtMs = 0L

    private val _levelStatus = MutableStateFlow<String?>(null)
    /** What the last import did, for the row under Output. */
    val levelStatus: StateFlow<String?> = _levelStatus.asStateFlow()

    private val _levelTracks = MutableStateFlow(0)
    val levelTracks: StateFlow<Int> = _levelTracks.asStateFlow()

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
                _connectionState.value = state
                if (state is ConnectionState.Up) {
                    _art.value = runCatching { conn.withClient { container.artFor(it) } }.getOrNull()
                    lastOutput = runCatching { conn.withClient { it.outputs().firstOrNull() } }.getOrNull()
                    _output.value = lastOutput
                    if (!librarySynced) {
                        librarySynced = true
                        viewModelScope.launch {
                            _syncingLibrary.value = true
                            runCatching {
                                val tracks = conn.withClient { it.listAllInfo() }
                                if (tracks.isNotEmpty()) container.library.replaceAll(tracks)
                            }
                            _syncingLibrary.value = false
                        }
                    }
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
        loudness = container.loudness.open()
        _levelTracks.value = loudness?.trackCount ?: 0
        startLevelTicker()
    }

    /**
     * The meter ticks faster than `status` polls: the envelope is 5 Hz, and a 2 Hz
     * readout would make the bars step instead of move. The position is extrapolated
     * from the last `status` with the monotonic clock, never invented.
     */
    private fun startLevelTicker() {
        levelJob?.cancel()
        levelJob = viewModelScope.launch {
            while (isActive) {
                delay(LEVEL_MS)
                val index = loudness
                val ui = _ui.value
                if (index == null || ui.uri.isEmpty()) {
                    if (ui.level != null) _ui.value = ui.copy(level = null)
                    continue
                }
                val offsetMs = if (ui.isDsd) settings.value.meterOffsetDsdMs else settings.value.meterOffsetPcmMs
                // Paused keeps the frame at the paused position: a needle at rest is
                // truthful, where "No level data" would blame a missing envelope.
                val clocked = if (ui.isPlaying) {
                    lastElapsed + (android.os.SystemClock.elapsedRealtime() - lastElapsedAtMs) / 1000.0
                } else {
                    lastElapsed
                }
                val position = clocked - offsetMs / 1000.0
                val level = index.levelAt(ui.uri, position)
                if (level != ui.level) _ui.value = _ui.value.copy(level = level)
            }
        }
    }

    /** Reads an imported `loudness.bin` from the file picker. */
    fun importLoudness(input: java.io.InputStream) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                container.loudness.importFrom(input)
            }
            when (result) {
                is dev.andrew.plinth.data.loudness.ImportResult.Imported -> {
                    loudness = container.loudness.open()
                    _levelTracks.value = loudness?.trackCount ?: 0
                    _levelStatus.value = "${result.tracks} tracks imported"
                }
                is dev.andrew.plinth.data.loudness.ImportResult.Rejected ->
                    _levelStatus.value = result.reason
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
            if (song != null) lastTrack = song
            _ui.value = _ui.value.merge(status, song).copy(
                strip = dev.andrew.plinth.data.model.SignalVerdicts.stripCells(status, lastTrack, lastOutput),
            )
            lastElapsed = status.elapsed ?: lastElapsed
            lastElapsedAtMs = android.os.SystemClock.elapsedRealtime()
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
            songId = status.songId ?: base.songId,
            isDsd = format?.isDsd == true,
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

    /** Album rows are played as a queue: clear, add in order, start at [index]. */
    fun playAlbum(tracks: List<dev.andrew.plinth.data.model.Track>, index: Int) {
        val conn = connection ?: return
        viewModelScope.launch {
            runCatching {
                conn.withClient { client ->
                    client.clearQueue()
                    tracks.forEach { client.add(it.uri) }
                    client.play(index)
                }
            }
            runCatching { refresh(conn) }
        }
    }

    fun albumTracks(albumId: String): kotlinx.coroutines.flow.Flow<List<dev.andrew.plinth.data.model.Track>> =
        container.library.albumTracks(albumId)

    fun artistTracks(artist: String): kotlinx.coroutines.flow.Flow<List<dev.andrew.plinth.data.model.Track>> =
        container.library.artistTracks(artist)

    fun folderTracks(folder: String): kotlinx.coroutines.flow.Flow<List<dev.andrew.plinth.data.model.Track>> =
        container.library.folderTracks(folder)

    /** One track, alone in the queue — what tapping a single search result should do. */
    fun playNow(uri: String) = command { client ->
        client.clearQueue()
        client.add(uri)
        client.play(0)
    }

    fun queueTrack(uri: String) = command { client -> client.add(uri) }

    fun queueAll(tracks: List<dev.andrew.plinth.data.model.Track>) = command { client ->
        tracks.forEach { client.add(it.uri) }
    }

    fun queuePlay(pos: Int) = command { client -> client.play(pos) }

    fun queueRemove(id: Int) = command { client -> client.deleteId(id) }

    /** [from] and [to] are queue positions; MPD moves by song id. */
    fun queueMove(from: Int, to: Int) = command { client ->
        val id = _queue.value.getOrNull(from)?.id ?: return@command
        client.moveId(id, to)
    }

    fun queueClear() = command { client -> client.clearQueue() }

    fun saveQueue(name: String) = command { client ->
        if (name.isNotBlank()) client.savePlaylist(name.trim())
    }

    /** Saved playlists live on the streamer; the dialog needs them on demand. */
    fun fetchPlaylists(onResult: (List<String>) -> Unit) {
        val conn = connection ?: return onResult(emptyList())
        viewModelScope.launch {
            onResult(runCatching { conn.withClient { it.listPlaylists() } }.getOrDefault(emptyList()))
        }
    }

    fun loadPlaylist(name: String) = command { client -> client.loadPlaylist(name) }

    fun setStayAwake(on: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(stayAwakeWhileCharging = on) }
        }
    }

    fun setMeterOffset(dsd: Boolean, ms: Int) {
        viewModelScope.launch {
            container.settings.update {
                if (dsd) it.copy(meterOffsetDsdMs = ms) else it.copy(meterOffsetPcmMs = ms)
            }
        }
    }

    fun setServer(host: String, port: Int) {
        viewModelScope.launch {
            container.settings.update { it.copy(host = host, port = port) }
            connect(host, port)
        }
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
        const val LEVEL_MS = 100L
    }
}

@Suppress("UNCHECKED_CAST")
class PlayerViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlayerViewModel(container) as T
}
