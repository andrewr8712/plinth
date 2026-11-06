package dev.andrew.plinth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.andrew.plinth.data.model.Album
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.screens.LibraryScreen
import dev.andrew.plinth.ui.screens.LibraryTab
import dev.andrew.plinth.ui.theme.PlinthTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Long-press menus on the library's songs, albums and folders, driven on a device. */
@RunWith(AndroidJUnit4::class)
class LibraryMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private val tracks = listOf(
        Track("Mezzanine/01 - Angel.flac", "a1", 1, 1, "Angel", "Massive Attack",
            "Massive Attack", "Mezzanine", 1998, null, 378, null, "Mezzanine", 2),
        Track("Mezzanine/02 - Teardrop.flac", "a1", 1, 2, "Teardrop", "Massive Attack",
            "Massive Attack", "Mezzanine", 1998, null, 331, null, "Mezzanine", 2),
        Track("Dummy/01 - Mysterons.flac", "a2", 1, 1, "Mysterons", "Portishead",
            "Portishead", "Dummy", 1994, null, 305, null, "Dummy", 2),
    )

    private val albums = listOf(
        Album("a1", "Mezzanine", "Massive Attack", 1998, 2, null, "Mezzanine/01 - Angel.flac"),
        Album("a2", "Dummy", "Portishead", 1994, 1, null, "Dummy/01 - Mysterons.flac"),
    )

    private fun library(
        tab: LibraryTab,
        onQueueTrack: (Track) -> Unit = {},
        onShowDetails: (Track) -> Unit = {},
        onPlayTracks: (List<Track>) -> Unit = {},
        onQueueTracks: (List<Track>) -> Unit = {},
        onReplaceTracks: (List<Track>) -> Unit = {},
    ) {
        rule.setContent {
            PlinthTheme {
                LibraryScreen(
                    size = SurfaceSize.Compact,
                    tab = tab,
                    onTab = {},
                    albums = albums,
                    artists = listOf("Massive Attack", "Portishead"),
                    tracks = tracks,
                    folders = listOf("Dummy", "Mezzanine"),
                    onQueueTrack = onQueueTrack,
                    onShowDetails = onShowDetails,
                    onPlayTracks = onPlayTracks,
                    onQueueTracks = onQueueTracks,
                    onReplaceTracks = onReplaceTracks,
                )
            }
        }
    }

    @Test
    fun longPressingASongOffersPlayQueueAndDetails() {
        var queued: Track? = null
        library(LibraryTab.Tracks, onQueueTrack = { queued = it })

        rule.onAllNodesWithTag("all-track-row")[0].performTouchInput { longClick() }

        rule.onNodeWithTag("item-menu").assertIsDisplayed()
        rule.onNodeWithText("Play Song").assertIsDisplayed()
        rule.onNodeWithText("Add to Queue").assertIsDisplayed()
        rule.onNodeWithText("View Details").assertIsDisplayed()

        rule.onNodeWithTag("menu-add-to-queue").performClick()
        assertEquals(tracks[0], queued)
    }

    @Test
    fun viewDetailsCarriesThePressedTrack() {
        var details: Track? = null
        library(LibraryTab.Tracks, onShowDetails = { details = it })

        rule.onAllNodesWithTag("all-track-row")[1].performTouchInput { longClick() }
        rule.onNodeWithTag("menu-view-details").performClick()

        assertEquals(tracks[1], details)
    }

    @Test
    fun anAlbumReplacesTheQueueWithItsOwnTracks() {
        var replaced: List<Track> = emptyList()
        library(LibraryTab.Albums, onReplaceTracks = { replaced = it })

        rule.onAllNodesWithTag("album-tile")[0].performTouchInput { longClick() }

        rule.onNodeWithText("Play Album").assertIsDisplayed()
        rule.onNodeWithText("Add to Queue").assertIsDisplayed()
        rule.onNodeWithText("Replace Queue").assertIsDisplayed()

        rule.onNodeWithTag("menu-replace-queue").performClick()
        assertEquals(tracks.filter { it.albumId == "a1" }, replaced)
    }

    @Test
    fun aFolderQueuesItsOwnTracks() {
        var queued: List<Track> = emptyList()
        library(LibraryTab.Folders, onQueueTracks = { queued = it })

        rule.onAllNodesWithTag("folder-row")[0].performTouchInput { longClick() }

        rule.onNodeWithText("Play Folder").assertIsDisplayed()
        rule.onNodeWithTag("menu-add-to-queue").performClick()
        assertEquals(listOf(tracks[2]), queued)
    }
}
