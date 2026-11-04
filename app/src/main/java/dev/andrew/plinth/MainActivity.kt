package dev.andrew.plinth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.layout.rememberSurfaceSize
import dev.andrew.plinth.ui.theme.PlinthColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrew.plinth.ui.screens.MiniPlayer
import dev.andrew.plinth.ui.screens.NowPlayingSheet
import dev.andrew.plinth.ui.screens.AlbumScreen
import dev.andrew.plinth.ui.screens.LibraryScreen
import dev.andrew.plinth.ui.screens.NowPlayingScreen
import dev.andrew.plinth.ui.theme.PlinthTheme
import dev.andrew.plinth.ui.vm.PlayerViewModel
import dev.andrew.plinth.ui.vm.PlayerViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PlinthApp).container
        setContent {
            PlinthTheme {
                AppRoot(container)
            }
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
    var sheetOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        containerColor = PlinthColors.Base,
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
                )
            }
            composable("library") {
                val albums by vm.albums.collectAsStateWithLifecycle()
                val syncing by vm.syncingLibrary.collectAsStateWithLifecycle()
                LibraryScreen(
                    size = surface,
                    albums = albums,
                    syncing = syncing,
                    onOpenAlbum = { id -> nav.navigate("album/$id") },
                )
            }
            composable("album/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val tracks by vm.albumTracks(id).collectAsStateWithLifecycle(initialValue = emptyList())
                AlbumScreen(
                    title = tracks.firstOrNull()?.album.orEmpty(),
                    artist = tracks.firstOrNull()?.albumArtist.orEmpty(),
                    tracks = tracks,
                    onPlayTrack = { index -> vm.playAlbum(tracks, index) },
                )
            }
            composable("queue") { Placeholder("Queue") }
            composable("search") { Placeholder("Search") }
            composable("output") { Placeholder("Output") }
            composable("connection") { Placeholder("Connection") }
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
                onClick = {
                    nav.navigate(destination.route) {
                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
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

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(
            name,
            color = PlinthColors.Faint,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        )
    }
}
