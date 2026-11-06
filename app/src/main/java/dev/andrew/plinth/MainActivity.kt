package dev.andrew.plinth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.andrew.plinth.data.model.BackgroundPolicy
import dev.andrew.plinth.data.model.PlaybackState
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.data.playback.PlaybackEngine
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.layout.rememberSurfaceSize
import dev.andrew.plinth.ui.theme.PlinthColors
import dev.andrew.plinth.ui.topbar.observeTopBarInteraction
import dev.andrew.plinth.ui.topbar.rememberTopBarAlpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrew.plinth.ui.screens.MiniPlayer
import dev.andrew.plinth.ui.screens.NowPlayingSheet
import dev.andrew.plinth.ui.screens.AlbumScreen
import dev.andrew.plinth.ui.screens.ConnectionScreen
import dev.andrew.plinth.ui.screens.LibraryScreen
import dev.andrew.plinth.ui.screens.LibraryTab
import dev.andrew.plinth.ui.screens.NowPlayingScreen
import dev.andrew.plinth.ui.screens.OutputScreen
import dev.andrew.plinth.ui.screens.QueueScreen
import dev.andrew.plinth.ui.screens.SearchScreen
import dev.andrew.plinth.ui.screens.TrackDetailsSheet
import dev.andrew.plinth.ui.screens.TrackListScreen
import dev.andrew.plinth.ui.screens.albumsMatching
import dev.andrew.plinth.ui.theme.PlinthTheme
import dev.andrew.plinth.ui.vm.PlayerViewModel
import dev.andrew.plinth.ui.vm.PlayerViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var stayAwakeWhileCharging = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PlinthApp).container
        askForNotifications()
        observeBackgroundPolicy(container)
        setContent {
            PlinthTheme {
                AppRoot(container)
            }
        }
    }

    /**
     * The UI is one of the engine's two owners. Dropping the demand on stop is what
     * suspends reconnects when the app is backgrounded and no service is running;
     * the service supplies the other demand while it is up.
     */
    private fun observeBackgroundPolicy(container: AppContainer) {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.engine.setActive(PlaybackEngine.OWNER_UI, true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    container.engine.setActive(PlaybackEngine.OWNER_UI, false)
                }
            },
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    container.settings.flow.collect {
                        stayAwakeWhileCharging = it.stayAwakeWhileCharging
                    }
                }
                // The charger can be plugged in while nothing else is changing, so this
                // is a ticker rather than a state collector. One second is below the
                // screen timeout, and repeatOnLifecycle stops it when the UI is gone.
                launch {
                    while (isActive) {
                        applyScreenPolicy(container)
                        delay(1_000)
                    }
                }
            }
        }
    }

    private fun applyScreenPolicy(container: AppContainer) {
        val policy = BackgroundPolicy(stayAwakeWhileCharging, container.isCharging())
        val state = container.engine.player.value?.state ?: PlaybackState.Unknown
        if (policy.shouldKeepScreenOn(state)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private data class Destination(val route: String, val label: String)

private val destinations = listOf(
    Destination("now", "Now"),
    Destination("library", "Library"),
    Destination("queue", "Queue"),
    Destination("search", "Search"),
    Destination("output", "Output"),
)

/**
 * The shell: a bottom bar on a phone, nothing on the tablet. The tablet is an
 * instrument on a stand — chrome around the cover would only shrink it.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val surface = rememberSurfaceSize()
    val nav = rememberNavController()
    val vm: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = PlayerViewModelFactory(container),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val art by vm.art.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val levelStale by vm.levelStale.collectAsStateWithLifecycle()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    // The loudness envelope ticker is 10 Hz; make it follow the activity's visible
    // lifetime instead of running for the whole ViewModel.
    LifecycleStartEffect(Unit) {
        vm.startMeter()
        onStopOrDispose { vm.stopMeter() }
    }
    var sheetOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // The details sheet owns its own fetch: the track opens the sheet at once and the
    // tag list arrives underneath it, so a slow streamer never delays the press.
    var detailsTrack by remember { mutableStateOf<Track?>(null) }
    var detailsTags by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var detailsFailed by remember { mutableStateOf(false) }
    val showDetails: (Track) -> Unit = { track ->
        detailsTrack = track
        detailsTags = null
        detailsFailed = false
        detailsLoading = true
        vm.fetchTrackDetails(track.uri) { tags ->
            // A slow answer for the previously opened track must not land on this one.
            if (detailsTrack?.uri == track.uri) {
                detailsTags = tags
                detailsFailed = tags == null
                detailsLoading = false
            }
        }
    }
    // Incremented on every interaction; the top bar keys its idle countdown on it, so
    // a touch anywhere in the Scaffold restarts the fade without the observer having to
    // know which control was touched.
    var topBarInteractions by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableIntStateOf(0)
    }
    // Only the tablet has a fading bar; the phone's shell is left exactly as it was.
    val topBarInteraction = if (surface == SurfaceSize.Compact) {
        Modifier
    } else {
        Modifier.observeTopBarInteraction { topBarInteractions++ }
    }

    Scaffold(
        containerColor = PlinthColors.Base,
        modifier = topBarInteraction,
        topBar = {
            // The tablet has no bottom bar, so Search and Output would be unreachable
            // without this. Now playing stays chrome-free — it is the instrument.
            if (surface != SurfaceSize.Compact && currentRoute != "now") {
                TabletTopBar(nav, currentRoute) { topBarInteractions }
            }
        },
        bottomBar = {
            if (surface == SurfaceSize.Compact) {
                androidx.compose.foundation.layout.Column {
                    // The Now tab already shows the full surface; a mini player there
                    // would be the same information twice. It belongs on the other tabs.
                    if (currentRoute != "now") {
                        MiniPlayer(
                            ui = ui,
                            art = art,
                            onOpen = { sheetOpen = true },
                            onPlayPause = vm::playPause,
                        )
                    }
                    PlinthBottomBar(nav)
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = nav,
            startDestination = "now",
            modifier = Modifier.padding(insets),
        ) {
            composable("now") {
                NowPlayingScreen(
                    size = surface,
                    ui = ui,
                    queue = queue,
                    art = art,
                    onPlayPause = vm::playPause,
                    onPrev = vm::previous,
                    onNext = vm::next,
                    onSeek = vm::seek,
                    onOpenLibrary = { nav.navigate("library") },
                    onOpenQueue = { nav.navigate("queue") },
                    onRetry = vm::retry,
                    levelStale = levelStale,
                )
            }
            composable("library") {
                val albums by vm.albums.collectAsStateWithLifecycle()
                val artists by vm.artists.collectAsStateWithLifecycle()
                val tracks by vm.tracks.collectAsStateWithLifecycle()
                val folders by vm.folders.collectAsStateWithLifecycle()
                val syncing by vm.syncingLibrary.collectAsStateWithLifecycle()
                // The tab is screen state, not a route: switching tabs should not grow
                // the back stack, and back should return to now-playing in one press.
                var tab by androidx.compose.runtime.saveable.rememberSaveable {
                    androidx.compose.runtime.mutableStateOf(LibraryTab.Albums)
                }
                LibraryScreen(
                    size = surface,
                    tab = tab,
                    onTab = { tab = it },
                    albums = albums,
                    artists = artists,
                    tracks = tracks,
                    folders = folders,
                    art = art,
                    syncing = syncing,
                    connecting = !ui.connected && !ui.offline,
                    host = settings.host,
                    offline = ui.offline,
                    offlineReason = ui.offlineReason,
                    onRetry = vm::retry,
                    onResync = vm::resyncLibrary,
                    onOpenAlbum = { id -> nav.navigate("album/$id") },
                    onOpenArtist = { name -> nav.navigate("artist/${android.net.Uri.encode(name)}") },
                    onOpenFolder = { name -> nav.navigate("folder/${android.net.Uri.encode(name)}") },
                    onPlayTrack = { track -> vm.playNow(track.uri) },
                    onQueueTrack = { track -> vm.queueTrack(track.uri) },
                    onShowDetails = showDetails,
                    onPlayTracks = { vm.playCollection(it) },
                    onQueueTracks = { vm.queueAll(it) },
                    onReplaceTracks = { vm.replaceQueue(it) },
                )
            }
            composable("album/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val tracks by vm.albumTracks(id).collectAsStateWithLifecycle(initialValue = emptyList())
                AlbumScreen(
                    title = tracks.firstOrNull()?.album.orEmpty(),
                    artist = tracks.firstOrNull()?.albumArtist.orEmpty(),
                    tracks = tracks,
                    albumId = id,
                    art = art,
                    onPlayTrack = { index -> vm.playAlbum(tracks, index) },
                    onPlayAll = { vm.playAlbum(tracks, 0) },
                    onQueueAll = { vm.queueAll(tracks) },
                    onQueueTrack = { track -> vm.queueTrack(track.uri) },
                    onShowDetails = showDetails,
                )
            }
            composable("artist/{name}") { entry ->
                val name = android.net.Uri.decode(entry.arguments?.getString("name").orEmpty())
                val tracks by vm.artistTracks(name).collectAsStateWithLifecycle(initialValue = emptyList())
                TrackListScreen(
                    title = name,
                    subtitle = "",
                    tracks = tracks,
                    onPlay = { index -> vm.playAlbum(tracks, index) },
                    onQueue = { track -> vm.queueTrack(track.uri) },
                    onShowDetails = showDetails,
                    showAlbum = true,
                )
            }
            composable("folder/{name}") { entry ->
                val name = android.net.Uri.decode(entry.arguments?.getString("name").orEmpty())
                val tracks by vm.folderTracks(name).collectAsStateWithLifecycle(initialValue = emptyList())
                TrackListScreen(
                    title = name,
                    subtitle = "Folder",
                    tracks = tracks,
                    onPlay = { index -> vm.playAlbum(tracks, index) },
                    onQueue = { track -> vm.queueTrack(track.uri) },
                    onShowDetails = showDetails,
                    showAlbum = true,
                )
            }
            composable("queue") {
                var playlists by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(emptyList<String>())
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    vm.fetchPlaylists { playlists = it }
                }
                QueueScreen(
                    items = queue,
                    currentId = ui.songId,
                    playlists = playlists,
                    onPlay = vm::queuePlay,
                    onRemove = vm::queueRemove,
                    onMove = vm::queueMove,
                    onClear = vm::queueClear,
                    onSave = vm::saveQueue,
                    onLoad = vm::loadPlaylist,
                    onBrowse = { nav.goTopLevel("library") },
                )
            }
            composable("search") {
                val query by vm.query.collectAsStateWithLifecycle()
                val results by vm.searchResults.collectAsStateWithLifecycle()
                val albums by vm.albums.collectAsStateWithLifecycle()
                SearchScreen(
                    query = query,
                    albums = albumsMatching(albums, query),
                    tracks = results,
                    onQueryChange = vm::setQuery,
                    onOpenAlbum = { id -> nav.navigate("album/$id") },
                    onPlayTrack = { track -> vm.playNow(track.uri) },
                    onQueueTrack = { track -> vm.queueTrack(track.uri) },
                    onShowDetails = showDetails,
                )
            }
            composable("output") {
                val output by vm.output.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val levelTracks by vm.levelTracks.collectAsStateWithLifecycle()
                val levelStatus by vm.levelStatus.collectAsStateWithLifecycle()
                val libraryTracks by vm.tracks.collectAsStateWithLifecycle()
                val context = androidx.compose.ui.platform.LocalContext.current
                val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    uri?.let { chosen ->
                        context.contentResolver.openInputStream(chosen)?.let { stream ->
                            vm.importLoudness(stream)
                        }
                    }
                }
                OutputScreen(
                    output = output,
                    settings = settings,
                    stateLabel = ui.stateLabel,
                    isDsd = ui.isDsd,
                    levelTracks = levelTracks,
                    levelStatus = levelStatus,
                    libraryTracks = libraryTracks.size,
                    levelStale = levelStale,
                    onStayAwake = vm::setStayAwake,
                    onOpenConnection = { nav.navigate("connection") },
                    onImportLoudness = { picker.launch(arrayOf("*/*")) },
                    onMeterOffset = vm::setMeterOffset,
                )
            }
            composable("connection") {
                val settings by vm.settings.collectAsStateWithLifecycle()
                val state by vm.connectionState.collectAsStateWithLifecycle()
                ConnectionScreen(
                    initialHost = settings.host,
                    initialPort = settings.port,
                    initialState = state,
                    onConnect = { host, port ->
                        vm.setServer(host, port)
                        nav.popBackStack()
                    },
                )
            }
        }
    }
    detailsTrack?.let { track ->
        TrackDetailsSheet(
            track = track,
            tags = detailsTags,
            loading = detailsLoading,
            failed = detailsFailed,
            onDismiss = { detailsTrack = null },
        )
    }
    if (sheetOpen) {
        NowPlayingSheet(
            ui = ui,
            queue = queue,
            art = art,
            onDismiss = { sheetOpen = false },
            onPlayPause = vm::playPause,
            onPrev = vm::previous,
            onNext = vm::next,
            onSeek = vm::seek,
        )
    }
}

@Composable
private fun PlinthBottomBar(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    NavigationBar(containerColor = PlinthColors.Plate) {
        destinations.forEach { destination ->
            val selected = current == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { nav.goTopLevel(destination.route) },
                icon = { DestinationGlyph(destination.route, selected) },
                label = {
                    Text(
                        destination.label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PlinthColors.Accent,
                    selectedTextColor = PlinthColors.Text,
                    indicatorColor = PlinthColors.Accent.copy(alpha = 0.16f),
                    unselectedIconColor = PlinthColors.Faint,
                    unselectedTextColor = PlinthColors.Faint,
                ),
                modifier = Modifier.testTag("bottom-${destination.route}"),
            )
        }
    }
}

/**
 * The bar's icons are drawn, not borrowed. The disc and the three-step plinth
 * are the launcher icon's own shapes; the other three share the same solid
 * weight so the row reads as one set rather than five unrelated glyphs.
 */
@Composable
private fun DestinationGlyph(route: String, selected: Boolean) {
    val color = if (selected) PlinthColors.Accent else PlinthColors.Faint
    Canvas(Modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        val stroke = 2.2f * unit
        when (route) {
            "now" -> {
                // The launcher's play disc, with the bar showing through the mark.
                drawCircle(
                    color = color,
                    radius = 7.2f * unit,
                    center = Offset(12f * unit, 12f * unit),
                )
                val play = Path().apply {
                    moveTo(10.4f * unit, 8.6f * unit)
                    lineTo(10.4f * unit, 15.4f * unit)
                    lineTo(15.5f * unit, 12f * unit)
                    close()
                }
                drawPath(play, PlinthColors.Plate)
            }
            "library" -> {
                // The plinth's three steps, narrowed to the top.
                drawBar(color, 7.6f * unit, 12.2f * unit, 8.8f * unit, 2.4f * unit, unit)
                drawBar(color, 7.0f * unit, 15.4f * unit, 10.0f * unit, 2.4f * unit, unit)
                drawBar(color, 6.3f * unit, 18.6f * unit, 11.4f * unit, 2.4f * unit, unit)
            }
            "queue" -> {
                for (i in 0..2) {
                    val y = (8.4f + i * 3.6f) * unit
                    drawCircle(
                        color = color,
                        radius = 1.1f * unit,
                        center = Offset(5.4f * unit, y),
                    )
                    drawBar(color, 8.2f * unit, y - 1.1f * unit, 10.2f * unit, 2.2f * unit, unit)
                }
            }
            "search" -> {
                drawCircle(
                    color = color,
                    radius = 5.4f * unit,
                    center = Offset(10.6f * unit, 10.6f * unit),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color,
                    start = Offset(14.8f * unit, 14.8f * unit),
                    end = Offset(18.6f * unit, 18.6f * unit),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            "output" -> {
                // The VU meter: three bars, bottom-aligned.
                val heights = floatArrayOf(6.0f, 10.4f, 7.6f)
                heights.forEachIndexed { i, height ->
                    val x = (7.0f + i * 4.0f) * unit
                    drawBar(
                        color,
                        x - 1.1f * unit,
                        (19.2f - height) * unit,
                        2.2f * unit,
                        height * unit,
                        unit,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawBar(
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    unit: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(1.1f * unit),
    )
}

/** The tablet's only chrome: where else you can go. It fades while idle. */
@Composable
private fun TabletTopBar(
    nav: NavHostController,
    current: String?,
    // A lambda, not the value: reading the counter here keeps the per-touch
    // recomposition inside the bar instead of rebuilding AppRoot's whole graph.
    interactionSignal: () -> Int,
) {
    val alpha = rememberTopBarAlpha(current, interactionSignal())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            // targetSdk 35 is edge-to-edge by mandate, so the bar must inset itself or
            // it draws under the clock and battery.
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(PlinthColors.Base)
            .padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Plinth",
            color = PlinthColors.Faint,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        destinations.filter { it.route != "now" }.forEach { destination ->
            val selected = current == destination.route
            Text(
                text = destination.label,
                color = if (selected) PlinthColors.Text else PlinthColors.Faint,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .selectable(
                        selected = selected,
                        onClick = { nav.goTopLevel(destination.route) },
                        role = Role.Tab,
                    )
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("top-${destination.route}"),
            )
        }
    }
}

/** Top-level destinations replace each other rather than stacking up. */
private fun NavHostController.goTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
