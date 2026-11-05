package dev.andrew.plinth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.andrew.plinth.data.loudness.StereoLevel
import dev.andrew.plinth.data.model.Album
import dev.andrew.plinth.ui.components.ArtworkPlate
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.screens.LibraryScreen
import dev.andrew.plinth.ui.screens.LibraryTab
import dev.andrew.plinth.ui.screens.NowPlayingScreen
import dev.andrew.plinth.ui.screens.QueueScreen
import dev.andrew.plinth.ui.screens.SearchScreen
import dev.andrew.plinth.ui.theme.PlinthTheme
import dev.andrew.plinth.ui.vm.PlayerUi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The failure states, on the device. The offline surface has to keep the cached
 * library usable and stay honest about what it no longer knows; the empty surfaces
 * have to name the cause and offer one action instead of a spinner.
 */
@RunWith(AndroidJUnit4::class)
class OfflineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun album(id: String, title: String) = Album(
        id = id,
        title = title,
        artist = "Artist",
        year = 2016,
        trackCount = 10,
        edition = null,
        firstUri = "Artist/$title/01.flac",
    )

    private fun ui(
        offline: Boolean = false,
        isPlaying: Boolean = true,
        level: StereoLevel? = null,
    ) = PlayerUi(
        title = "Teardrop",
        artist = "Massive Attack",
        album = "Mezzanine",
        albumId = "a1",
        uri = "Mezzanine/02 - Teardrop.flac",
        elapsed = 61.0,
        duration = 331.0,
        isPlaying = isPlaying,
        connected = !offline,
        offline = offline,
        offlineReason = if (offline) "Streamer not answering" else "",
        level = level,
    )

    @Test
    fun offlineChipShowsAndCachedLibraryStaysBrowsable() {
        rule.setContent {
            PlinthTheme {
                LibraryScreen(
                    size = SurfaceSize.Expanded,
                    tab = LibraryTab.Albums,
                    onTab = {},
                    albums = listOf(album("1", "Mezzanine"), album("2", "Blue"), album("3", "Raven")),
                    artists = emptyList(),
                    tracks = emptyList(),
                    folders = emptyList(),
                    offline = true,
                    offlineReason = "Streamer not answering",
                    onRetry = {},
                )
            }
        }

        rule.onNodeWithTag("offline-chip").assertIsDisplayed()
        rule.onNodeWithText("Streamer not answering").assertIsDisplayed()
        rule.onAllNodesWithTag("album-tile").assertCountEquals(3)
    }

    @Test
    fun transportControlsAreDisabledWhileOfflineNotRemoved() {
        rule.setContent {
            PlinthTheme {
                NowPlayingScreen(size = SurfaceSize.Expanded, ui = ui(offline = true, isPlaying = false))
            }
        }

        rule.onNodeWithContentDescription("Play").assertExists()
        rule.onNodeWithContentDescription("Play").assertIsNotEnabled()
        rule.onNodeWithTag("offline-chip").assertIsDisplayed()
    }

    @Test
    fun pausedSwapsTheGlyphAndFlattensTheMeter() {
        rule.setContent {
            PlinthTheme {
                NowPlayingScreen(
                    size = SurfaceSize.Expanded,
                    ui = ui(isPlaying = false, level = StereoLevel(196, 190, 200)),
                )
            }
        }

        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
        rule.onNodeWithTag("level-meter").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Idle"),
        )
    }

    @Test
    fun noArtworkDrawsTheMonogramPlateWithItsLabel() {
        rule.setContent {
            PlinthTheme {
                Box(Modifier.size(200.dp)) {
                    ArtworkPlate(label = "Dire Straits", noArtwork = true)
                }
            }
        }

        rule.onNodeWithText("DS").assertIsDisplayed()
        rule.onNodeWithText("No artwork").assertIsDisplayed()
    }

    @Test
    fun emptyLibraryNamesTheShareAndOffersRetry() {
        var retried = false
        rule.setContent {
            PlinthTheme {
                LibraryScreen(
                    size = SurfaceSize.Compact,
                    tab = LibraryTab.Albums,
                    onTab = {},
                    albums = emptyList(),
                    artists = emptyList(),
                    tracks = emptyList(),
                    folders = emptyList(),
                    onRetry = { retried = true },
                )
            }
        }

        rule.onNodeWithText("Library is empty").assertIsDisplayed()
        rule.onNodeWithTag("empty-action").performClick()
        assertTrue("Retry must reach the connection", retried)
    }

    @Test
    fun emptyQueueOffersBrowsing() {
        var browsed = false
        rule.setContent {
            PlinthTheme { QueueScreen(items = emptyList(), onBrowse = { browsed = true }) }
        }

        rule.onNodeWithText("Nothing queued").assertIsDisplayed()
        rule.onNodeWithTag("empty-action").performClick()
        assertTrue("Browse albums must reach the library", browsed)
    }

    @Test
    fun noSearchResultsEchoTheQueryAndClearIt() {
        var query by mutableStateOf("teardrp")
        rule.setContent {
            PlinthTheme {
                SearchScreen(
                    query = query,
                    albums = emptyList(),
                    tracks = emptyList(),
                    onQueryChange = { query = it },
                )
            }
        }

        rule.onNodeWithText("No matches for “teardrp”").assertIsDisplayed()
        rule.onNodeWithTag("empty-action").performClick()
        rule.onNodeWithText("Type to search").assertIsDisplayed()
    }
}
