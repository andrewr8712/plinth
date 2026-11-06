package dev.andrew.plinth.ui.vm

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.andrew.plinth.AppContainer
import dev.andrew.plinth.data.model.PlaybackState
import dev.andrew.plinth.data.model.PlayerState
import dev.andrew.plinth.data.mpd.ConnectionState
import dev.andrew.plinth.data.mpd.MpdClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
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
    /** True only for [dev.andrew.plinth.data.mpd.ConnectionState.Unavailable]. */
    val offline: Boolean = false,
    /** Human copy for the failure chip; empty when there is nothing to report. */
    val offlineReason: String = "",
)

/**
 * The UI's view of [dev.andrew.plinth.data.playback.PlaybackEngine]. The engine owns
 * the socket so the playback service can keep it alive without an activity; this
 * maps its state into [PlayerUi] and keeps the library, art and queue in step.
 */
class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    private val engine = container.engine

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private var lastQueueVersion: Int = Int.MIN_VALUE
    private var lastOutput: dev.andrew.plinth.data.model.OutputInfo? = null

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResults: StateFlow<List<dev.andrew.plinth.data.model.Track>> = _query
        .debounce(150)
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

    private val _levelStale = MutableStateFlow(false)
    /** True when the imported envelope predates the streamer's current database. */
    val levelStale: StateFlow<Boolean> = _levelStale.asStateFlow()

    private var libraryDbUpdate: Long? = null

    init {
        loudness = container.loudness.open()
        _levelTracks.value = loudness?.trackCount ?: 0

        viewModelScope.launch {
            val first = container.settings.flow.first()
            engine.connect(first.host, first.port)
        }
        viewModelScope.launch { engine.connection.collect(::onConnection) }
        viewModelScope.launch { engine.player.collect { state -> if (state != null) onStatus(state) } }
        viewModelScope.launch { engine.track.collect { song -> onTrack(song) } }
    }

    fun connect(host: String, port: Int) {
        engine.connect(host, port)
    }

    /** Starts the meter ticker for as long as the UI is visible. Idempotent. */
    fun startMeter() {
        if (levelJob?.isActive == true) return
        startLevelTicker()
    }

    /** Stops the meter ticker and drops the last frame; called when the UI stops. */
    fun stopMeter() {
        levelJob?.cancel()
        levelJob = null
        if (_ui.value.level != null) _ui.value = _ui.value.copy(level = null)
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
                if (index == null || ui.uri.isEmpty() || ui.offline || !ui.connected) {
                    // No contact, no evidence: do not keep extrapolating a position
                    // into the envelope while the streamer is unreachable or dialling.
                    if (ui.level != null) _ui.value = ui.copy(level = null)
                    continue
                }
                val offsetMs = if (ui.isDsd) settings.value.meterOffsetDsdMs else settings.value.meterOffsetPcmMs
                // Paused keeps the frame at the paused position: a needle at rest is
                // truthful, where "No level data" would blame a missing envelope.
                val clocked = if (ui.isPlaying) {
                    lastElapsed + (SystemClock.elapsedRealtime() - lastElapsedAtMs) / 1000.0
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
                    refreshLevelStaleness()
                }
                is dev.andrew.plinth.data.loudness.ImportResult.Rejected ->
                    _levelStatus.value = result.reason
            }
        }
    }

    private fun refreshLevelStaleness() {
        _levelStale.value = dev.andrew.plinth.data.loudness.isLoudnessStale(
            envelopeDbUpdate = loudness?.dbUpdate ?: 0,
            libraryDbUpdate = libraryDbUpdate,
        )
    }

    private fun onConnection(state: ConnectionState) {
        _connectionState.value = state
        if (state is ConnectionState.Up) {
            onUp()
        } else {
            _art.value = null
        }
        _ui.value = _ui.value.copy(
            connected = state is ConnectionState.Up,
            offline = state is ConnectionState.Unavailable,
            offlineReason = offlineCopy(state).orEmpty(),
            stateLabel = when (state) {
                is ConnectionState.Up -> "Connected"
                is ConnectionState.Connecting -> "Connecting…"
                is ConnectionState.Unavailable -> state.message
            },
        )
    }

    private fun onUp() {
        viewModelScope.launch {
            _art.value = runCatching { engine.withClient { container.artFor(it) } }.getOrNull()
            lastOutput = runCatching { engine.withClient { it.outputs().firstOrNull() } }.getOrNull()
            _output.value = lastOutput
            // A paused or stopped streamer returns an equal status every poll, so the
            // StateFlow does not emit again; the strip computed before the outputs
            // arrived would otherwise keep saying "USB · output".
            engine.player.value?.let { status ->
                _ui.value = _ui.value.copy(
                    strip = dev.andrew.plinth.data.model.SignalVerdicts.stripCells(
                        status,
                        engine.track.value,
                        lastOutput,
                    ),
                )
            }
            libraryDbUpdate = runCatching { engine.withClient { it.dbUpdate() } }.getOrNull()
            refreshLevelStaleness()
            if (!librarySynced) syncLibrary()
        }
    }

    /**
     * Reads the whole library and only then calls the cache synced. The flag used to be
     * set before the read, so one failed `listallinfo` meant "Library is empty" with a
     * Retry that only redialled, and every later reconnect skipped the sync.
     */
    private suspend fun syncLibrary() {
        if (_syncingLibrary.value) return
        _syncingLibrary.value = true
        val synced = runCatching {
            val tracks = engine.withClient { it.listAllInfo() }
            if (tracks.isEmpty()) return@runCatching false
            container.library.replaceAll(tracks)
            true
        }.getOrDefault(false)
        _syncingLibrary.value = false
        librarySynced = synced
    }

    /** The empty library's action: re-read the share without redialling the socket. */
    fun resyncLibrary() {
        viewModelScope.launch {
            libraryDbUpdate = runCatching { engine.withClient { it.dbUpdate() } }.getOrNull()
            refreshLevelStaleness()
            syncLibrary()
        }
    }

    private fun onStatus(status: PlayerState) {
        val format = status.format
        val base = _ui.value
        _ui.value = base.copy(
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
            strip = dev.andrew.plinth.data.model.SignalVerdicts.stripCells(
                status,
                engine.track.value?.let { track ->
                    // MPD reports no Format for DFF/DSF; the library guesses DSD64 from
                    // the extension, but the stream knows the real rate (DSD128 here).
                    // While a DSD file plays, the stream is the only evidence.
                    if (track.format?.isDsd == true && format?.isDsd == true) {
                        track.copy(format = format)
                    } else {
                        track
                    }
                },
                lastOutput,
            ),
        )
        lastElapsed = status.elapsed ?: lastElapsed
        lastElapsedAtMs = SystemClock.elapsedRealtime()
        if (status.queueVersion != lastQueueVersion) {
            lastQueueVersion = status.queueVersion
            viewModelScope.launch {
                runCatching { _queue.value = engine.withClient { it.playlistInfo() } }
            }
        }
    }

    private fun onTrack(song: dev.andrew.plinth.data.model.Track?) {
        if (song == null) {
            // The streamer has no current song; keep nothing that claims otherwise.
            _ui.value = _ui.value.copy(
                title = "",
                artist = "",
                album = "",
                albumId = "",
                uri = "",
                elapsed = null,
                duration = null,
                songId = null,
                formatLine = null,
                isDsd = false,
                strip = null,
                level = null,
            )
            return
        }
        _ui.value = _ui.value.copy(
            title = song.title.ifBlank { song.uri.substringAfterLast('/') },
            artist = song.artist.ifBlank { song.albumArtist },
            album = song.album,
            albumId = song.albumId,
            uri = song.uri,
        )
    }

    /** Album rows are played as a queue: clear, add in order, start at [index]. */
    fun playAlbum(tracks: List<dev.andrew.plinth.data.model.Track>, index: Int) {
        viewModelScope.launch {
            runCatching {
                engine.withClient { client ->
                    client.clearQueue()
                    tracks.forEach { client.add(it.uri) }
                    client.play(index)
                }
            }
            runCatching { engine.refresh() }
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

    /**
     * Replace the queue with [tracks] and start at the top — "Play Song" and "Play Album".
     * An empty set is a no-op: clearing the queue on a stale row would stop the music
     * for nothing.
     */
    fun playCollection(tracks: List<dev.andrew.plinth.data.model.Track>) = command { client ->
        if (tracks.isEmpty()) return@command
        client.clearQueue()
        tracks.forEach { client.add(it.uri) }
        client.play(0)
    }

    /** Swap the queue for [tracks] but leave it stopped — "Replace Queue". */
    fun replaceQueue(tracks: List<dev.andrew.plinth.data.model.Track>) = command { client ->
        if (tracks.isEmpty()) return@command
        client.clearQueue()
        tracks.forEach { client.add(it.uri) }
    }

    /** The whole tag set for one file, or null when the streamer cannot answer. */
    fun fetchTrackDetails(uri: String, onResult: (List<Pair<String, String>>?) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { engine.withClient { it.lsinfo(uri) } }.getOrNull())
        }
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
        viewModelScope.launch {
            onResult(runCatching { engine.withClient { it.listPlaylists() } }.getOrDefault(emptyList()))
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
        val unchanged = settings.value.host == host && settings.value.port == port
        viewModelScope.launch {
            container.settings.update { it.copy(host = host, port = port) }
            if (unchanged) {
                // Connect on the Connection screen is also the redial after a failure.
                engine.retry()
                return@launch
            }
            // A different streamer must not inherit this one's queue, output or cache.
            librarySynced = false
            lastQueueVersion = Int.MIN_VALUE
            _queue.value = emptyList()
            _output.value = null
            lastOutput = null
            _ui.value = _ui.value.copy(
                title = "",
                artist = "",
                album = "",
                albumId = "",
                uri = "",
                elapsed = null,
                duration = null,
                songId = null,
                level = null,
                strip = null,
            )
            engine.connect(host, port)
        }
    }

    /** Cancels the reconnect backoff and dials again, from the offline chip. */
    fun retry() = engine.retry()

    fun playPause() = engine.playPause()

    fun next() = engine.next()

    fun previous() = engine.previous()

    fun seek(seconds: Double) = engine.seek(seconds)

    private fun command(block: suspend (MpdClient) -> Unit) {
        viewModelScope.launch {
            runCatching { engine.withClient { block(it) } }
            // draw the new state immediately rather than waiting for the next tick
            runCatching { engine.refresh() }
        }
    }

    private companion object {
        const val LEVEL_MS = 100L
    }
}

/**
 * The chip's copy, kept out of Android so it can be tested on the JVM. A timeout and a
 * refused socket read differently to the owner: one is the streamer asleep, the other
 * a wrong address.
 */
internal fun offlineCopy(state: ConnectionState): String? = when (state) {
    is ConnectionState.Unavailable -> {
        val message = state.message.lowercase()
        when {
            "refused" in message -> "Streamer refused the connection"
            "route" in message || "unreachable" in message || "network" in message ->
                "Streamer unreachable"
            else -> "Streamer not answering"
        }
    }
    else -> null
}

@Suppress("UNCHECKED_CAST")
class PlayerViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlayerViewModel(container) as T
}
