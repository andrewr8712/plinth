package dev.andrew.plinth.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.andrew.plinth.data.model.Album
import dev.andrew.plinth.data.model.OutputInfo
import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.data.settings.Settings
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.screens.AlbumScreen
import dev.andrew.plinth.ui.screens.LibraryScreen
import dev.andrew.plinth.ui.screens.LibraryTab
import dev.andrew.plinth.ui.screens.OutputScreen
import dev.andrew.plinth.ui.screens.QueueScreen
import dev.andrew.plinth.ui.screens.SearchScreen
import dev.andrew.plinth.ui.screens.albumsMatching
import dev.andrew.plinth.ui.theme.PlinthTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Task 15's screens, driven with fake data — the data layer has its own JVM tests. */
@RunWith(AndroidJUnit4::class)
class LibrarySurfacesTest {

    @get:Rule
    val rule = createComposeRule()

    private val albums = listOf(
        Album("a1", "Mezzanine", "Massive Attack", 1998, 2, null, "Mezzanine/01 - Angel.flac"),
        Album("a2", "Dummy", "Portishead", 1994, 1, null, "Dummy/01 - Mysterons.flac"),
        Album("a3", "Raven", "Steven Wilson", 2013, 1, "5.1", "Raven/01 - Luminol.flac"),
    )

    private val tracks = listOf(
        Track("Mezzanine/01 - Angel.flac", "a1", 1, 1, "Angel", "Massive Attack", "Massive Attack",
            "Mezzanine", 1998, null, 378, null, "Mezzanine", 2),
        Track("Mezzanine/02 - Teardrop.flac", "a1", 1, 2, "Teardrop", "Massive Attack", "Massive Attack",
            "Mezzanine", 1998, null, 331, null, "Mezzanine", 2),
        Track("Dummy/01 - Mysterons.flac", "a2", 1, 1, "Mysterons", "Portishead", "Portishead",
            "Dummy", 1994, null, 305, null, "Dummy", 2),
    )

    private val artists = listOf("Massive Attack", "Portishead", "Steven Wilson")
    private val folders = listOf("Dummy", "Mezzanine", "Raven")

    private fun library(tab: LibraryTab, onTab: (LibraryTab) -> Unit = {}) {
        rule.setContent {
            PlinthTheme {
                LibraryScreen(
                    size = SurfaceSize.Expanded,
                    tab = tab,
                    onTab = onTab,
                    albums = albums,
                    artists = artists,
                    tracks = tracks,
                    folders = folders,
                )
            }
        }
    }

    @Test
    fun everyAlbumIsATileThatAsksForItsCover() {
        library(LibraryTab.Albums)
        rule.onAllNodesWithTag("album-tile").assertCountEquals(3)
        // ArtRepository is null here, so the tile still draws an honest placeholder plate.
        rule.onAllNodesWithTag("cover", useUnmergedTree = true).assertCountEquals(3)
        rule.onNodeWithText("Mezzanine").assertIsDisplayed()
    }

    @Test
    fun theFourTabsShowAlbumsArtistsTracksAndFolders() {
        var tab by mutableStateOf(LibraryTab.Albums)
        val switched = mutableListOf<LibraryTab>()
        rule.setContent {
            PlinthTheme {
                LibraryScreen(
                    size = SurfaceSize.Expanded,
                    tab = tab,
                    onTab = {
                        switched += it
                        tab = it
                    },
                    albums = albums,
                    artists = artists,
                    tracks = tracks,
                    folders = folders,
                )
            }
        }

        rule.onNodeWithTag("tab-Artists").performClick()
        rule.onNodeWithText("3 artists").assertIsDisplayed()
        rule.onAllNodesWithTag("artist-row", useUnmergedTree = true).assertCountEquals(3)

        rule.onNodeWithTag("tab-Tracks").performClick()
        rule.onNodeWithText("3 tracks").assertIsDisplayed()
        rule.onAllNodesWithTag("all-track-row", useUnmergedTree = true).assertCountEquals(3)

        rule.onNodeWithTag("tab-Folders").performClick()
        rule.onNodeWithText("3 folders").assertIsDisplayed()
        rule.onAllNodesWithTag("folder-row", useUnmergedTree = true).assertCountEquals(3)

        rule.onNodeWithTag("tab-Albums").performClick()
        rule.onNodeWithText("3 albums").assertIsDisplayed()
        rule.onAllNodesWithTag("album-tile", useUnmergedTree = true).assertCountEquals(3)

        assertEquals(
            listOf(LibraryTab.Artists, LibraryTab.Tracks, LibraryTab.Folders, LibraryTab.Albums),
            switched,
        )
    }

    @Test
    fun searchFiltersAsYouType() {
        var query by mutableStateOf("")
        rule.setContent {
            PlinthTheme {
                SearchScreen(
                    query = query,
                    albums = albumsMatching(albums, query),
                    tracks = tracks.filter { it.title.startsWith(query, ignoreCase = true) },
                    onQueryChange = { query = it },
                )
            }
        }

        rule.onNodeWithText("Type to search").assertIsDisplayed()
        rule.onNodeWithTag("search-field").performTextInput("Teardrop")
        // the query also lives in the field's own text, so count rows instead of text
        rule.onAllNodesWithTag("search-track-row", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun queueRowsCanBeRemovedAndReordered() {
        val removed = mutableListOf<Int>()
        val moved = mutableListOf<Pair<Int, Int>>()
        var played = -1
        rule.setContent {
            PlinthTheme {
                Column {
                    QueueScreen(
                        items = listOf(
                            QueueItem(11, 0, "a.flac", "Angel", "Massive Attack", "Mezzanine", 378),
                            QueueItem(22, 1, "b.flac", "Mysterons", "Portishead", "Dummy", 305),
                        ),
                        currentId = 11,
                        onPlay = { played = it },
                        onRemove = { removed += it },
                        onMove = { from, to -> moved += from to to },
                    )
                }
            }
        }

        rule.onAllNodesWithTag("queue-row").assertCountEquals(2)
        rule.onAllNodesWithContentDescription("Move down")[0].performClick()
        assertEquals(listOf(0 to 1), moved)

        rule.onAllNodesWithContentDescription("Remove from queue")[1].performClick()
        assertEquals(listOf(22), removed)

        rule.onAllNodesWithTag("queue-row-body")[0].performClick()
        assertEquals(0, played)
    }

    @Test
    fun savedPlaylistsCanBeLoadedFromTheQueue() {
        var loaded = ""
        rule.setContent {
            PlinthTheme {
                QueueScreen(
                    items = emptyList(),
                    playlists = listOf("Dinner", "Late night"),
                    onLoad = { loaded = it },
                )
            }
        }
        rule.onNodeWithTag("queue-load").performClick()
        rule.onAllNodesWithTag("playlist-row", useUnmergedTree = true).assertCountEquals(2)
        rule.onNodeWithText("Dinner").performClick()
        assertEquals("Dinner", loaded)
    }

    @Test
    fun outputScreenSaysWhereTheVolumeActuallyIs() {
        rule.setContent {
            PlinthTheme {
                OutputScreen(
                    output = OutputInfo(0, "Topping DX5 II", enabled = true, dopAttribute = true),
                    settings = Settings(stayAwakeWhileCharging = true),
                    stateLabel = "Playing",
                )
            }
        }
        rule.onNodeWithText("Topping DX5 II").assertIsDisplayed()
        rule.onNodeWithTag("no-mixer-note").assertIsDisplayed()
        rule.onNodeWithText("Volume is the DAC's knob; this streamer has no mixer.").assertIsDisplayed()
        rule.onNodeWithText("192.168.1.5:6600").assertDoesNotExist()
        rule.onNodeWithText("10.0.0.219:6600").assertIsDisplayed()
    }

    @Test
    fun albumScreenOffersPlayAllAndQueueAll() {
        var playAll = 0
        var queueAll = 0
        rule.setContent {
            PlinthTheme {
                AlbumScreen(
                    title = "Mezzanine",
                    artist = "Massive Attack",
                    tracks = tracks.filter { it.albumId == "a1" },
                    albumId = "a1",
                    onPlayAll = { playAll++ },
                    onQueueAll = { queueAll++ },
                )
            }
        }
        rule.onAllNodesWithTag("list-track-row").assertCountEquals(2)
        rule.onNodeWithTag("play-all").performClick()
        rule.onNodeWithTag("queue-all").performClick()
        assertEquals(1, playAll)
        assertEquals(1, queueAll)
    }
}
