package dev.andrew.plinth.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.ui.screens.MediumNowPlaying
import dev.andrew.plinth.ui.screens.MiniPlayer
import dev.andrew.plinth.ui.screens.NowPlayingSheet
import dev.andrew.plinth.ui.theme.PlinthTheme
import dev.andrew.plinth.ui.vm.PlayerUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneSurfacesTest {

    @get:Rule
    val rule = createComposeRule()

    private val queue = listOf(
        QueueItem(1, 0, "a.flac", "Angel", "Massive Attack", "Mezzanine", 378),
        QueueItem(2, 1, "b.flac", "Inertia Creeps", "Massive Attack", "Mezzanine", 351),
    )

    private fun ui(title: String) = PlayerUi(
        title = title,
        artist = "Massive Attack",
        album = "Mezzanine",
        albumId = "a1",
        uri = "Mezzanine/02 - Teardrop.flac",
        elapsed = 61.0,
        duration = 331.0,
        isPlaying = true,
        connected = true,
        strip = dev.andrew.plinth.data.model.StripCells("FLAC · 16/44", "USB · DX5 II", "Bit-perfect"),
    )

    private fun phoneRoot() {
        rule.setContent {
            var open by mutableStateOf(false)
            PlinthTheme {
                Column {
                    MiniPlayer(ui = ui("Teardrop"), art = null, onOpen = { open = true }, onPlayPause = {})
                    if (open) {
                        NowPlayingSheet(
                            ui = ui("Teardrop"),
                            queue = queue,
                            art = null,
                            onDismiss = { open = false },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun tappingTheMiniPlayerOpensTheSheetAndTheChevronClosesIt() {
        phoneRoot()
        rule.onNodeWithTag("mini-player").performClick()
        rule.onNodeWithTag("now-playing-sheet").assertIsDisplayed()

        rule.onNodeWithContentDescription("Close now playing").performClick()
        rule.onNodeWithTag("now-playing-sheet").assertDoesNotExist()
    }

    @Test
    fun sheetShowsUpNextAndNoMeter() {
        phoneRoot()
        rule.onNodeWithTag("mini-player").performClick()
        rule.onNodeWithText("Up next", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Stereo level meter").assertDoesNotExist()
    }

    @Test
    fun mediumWidthStacksTheSpine() {
        rule.setContent {
            PlinthTheme {
                Column(Modifier.size(760.dp, 980.dp)) {
                    MediumNowPlaying(ui = ui("Teardrop"), queue = queue, art = null)
                }
            }
        }
        rule.onNodeWithTag("queue-band").assertExists()
        rule.onNodeWithTag("cover").assertWidthIsEqualTo(760.dp - 80.dp)
    }
}
