package dev.andrew.plinth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.screens.NowPlayingScreen
import dev.andrew.plinth.ui.theme.PlinthTheme
import dev.andrew.plinth.ui.vm.PlayerUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class NowPlayingExpandedTest {

    @get:Rule
    val rule = createComposeRule()

    private val queue = listOf(
        QueueItem(1, 0, "a.flac", "Angel", "Massive Attack", "Mezzanine", 378),
        QueueItem(2, 1, "b.flac", "Inertia Creeps", "Massive Attack", "Mezzanine", 331),
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
        formatLine = "44.1 kHz · 16 bit · 2 ch",
        queueCount = queue.size,
    )

    private fun setScreen(title: String) {
        rule.setContent {
            PlinthTheme {
                NowPlayingScreen(
                    size = SurfaceSize.Expanded,
                    ui = ui(title),
                    queue = queue,
                )
            }
        }
    }

    @Test
    fun expandedShowsCoverTitleCentredTransportAndBand() {
        setScreen("Teardrop")

        rule.onNodeWithTag("cover").assertWidthIsEqualTo(420.dp)
        rule.onNodeWithText("Teardrop").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").assertExists()
        rule.onNodeWithText("Up next", substring = true).assertExists()
        rule.onNodeWithContentDescription("Stereo level meter").assertExists()

        // the play button is centred in the 584 dp column: same centre as the hairline
        val play = rule.onNodeWithTag("playPause").getUnclippedBoundsInRoot()
        val line = rule.onNodeWithTag("progressHairline").getUnclippedBoundsInRoot()
        val playCentre = ((play.left + play.right) / 2f).value
        val lineCentre = ((line.left + line.right) / 2f).value
        assertEquals("play must sit on the hairline's centre", lineCentre, playCentre, 1f)
    }

    @Test
    fun transportKeepsOnePositionWhenTheTitleWraps() {
        var title by mutableStateOf("Teardrop")
        rule.setContent {
            PlinthTheme { NowPlayingScreen(SurfaceSize.Expanded, ui(title), queue) }
        }

        val oneLine = rule.onNodeWithTag("playPause").getUnclippedBoundsInRoot().top

        rule.runOnIdle { title = "Teardrop (Live From The Rolling Thunder Revue)" }
        rule.waitForIdle()
        val twoLines = rule.onNodeWithTag("playPause").getUnclippedBoundsInRoot().top

        assertEquals(
            "the transport must not move when the title wraps",
            0f,
            abs(oneLine.value - twoLines.value),
            1f,
        )
    }
}
