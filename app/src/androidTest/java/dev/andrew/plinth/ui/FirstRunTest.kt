package dev.andrew.plinth.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.screens.LibraryScreen
import dev.andrew.plinth.ui.screens.LibraryTab
import dev.andrew.plinth.ui.theme.PlinthTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first run and the first sync. Before this, a cold start showed "Library is
 * empty" while it was still dialling — an error state for something that was only
 * slow.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunTest {

    @get:Rule
    val rule = createComposeRule()

    private fun library(
        connecting: Boolean = false,
        syncing: Boolean = false,
        host: String = "10.0.0.219",
    ) {
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
                    connecting = connecting,
                    syncing = syncing,
                    host = host,
                )
            }
        }
    }

    @Test
    fun connectingNamesTheHostAndShowsSkeletons() {
        library(connecting = true)

        rule.onNodeWithText("Connecting").assertIsDisplayed()
        rule.onNodeWithText("Reaching 10.0.0.219").assertIsDisplayed()
        rule.onNodeWithTag("library-skeleton").assertIsDisplayed()
        rule.onAllNodesWithText("Library is empty").assertCountEquals(0)
    }

    @Test
    fun firstSyncReadsAsReadingTheLibrary() {
        library(syncing = true)

        rule.onNodeWithText("Reading the library…").assertIsDisplayed()
        rule.onNodeWithTag("library-skeleton").assertIsDisplayed()
    }

    @Test
    fun aBlankHostStillNamesTheStreamerGenerically() {
        library(connecting = true, host = "")

        rule.onNodeWithText("Reaching the streamer").assertIsDisplayed()
    }
}
