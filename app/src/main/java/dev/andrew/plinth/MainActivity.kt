package dev.andrew.plinth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.andrew.plinth.data.model.BackgroundPolicy
import dev.andrew.plinth.data.model.PlaybackState
import dev.andrew.plinth.data.playback.PlaybackEngine
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.layout.rememberSurfaceSize
import dev.andrew.plinth.ui.theme.PlinthColors
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
            container.settings.flow.collect { stayAwakeWhileCharging = it.stayAwakeWhileCharging }
        }
        // The charger can be plugged in while nothing else is changing, so this is a
        // ticker rather than a state collector. One second is below the screen timeout.
        lifecycleScope.launch {
            while (isActive) {
                applyScreenPolicy(container)
                delay(1_000)
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
    var sheetOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        containerColor = PlinthColors.Base,
        topBar = {
            // The tablet has no bottom bar, so Search and Output would be unreachable
            // without this. Now playing stays chrome-free — it is the instrument.
            if (surface != SurfaceSize.Compact && currentRoute != "now") {
                TabletTopBar(nav, currentRoute)
            }
        },
        bottomBar = {
            if (surface == SurfaceSize.Compact) {
                androidx.compose.foundation.layout.Column {
                    MiniPlayer(
                        ui = ui,
                        art = art,
                        onOpen = { sheetOpen = true },
                        onPlayPause = vm::playPause,
                    )
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
                    onOpenAlbum = { id -> nav.navigate("album/$id") },
                    onOpenArtist = { name -> nav.navigate("artist/${android.net.Uri.encode(name)}") },
                    onOpenFolder = { name -> nav.navigate("folder/${android.net.Uri.encode(name)}") },
                    onPlayTrack = { track -> vm.playNow(track.uri) },
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
            NavigationBarItem(
                selected = current == destination.route,
                onClick = { nav.goTopLevel(destination.route) },
                icon = {},
                label = {
                    Text(
                        destination.label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

/** The tablet's only chrome: where else you can go. */
@Composable
private fun TabletTopBar(nav: NavHostController, current: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                    .clickable { nav.goTopLevel(destination.route) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
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
