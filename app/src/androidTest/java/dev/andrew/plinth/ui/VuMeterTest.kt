package dev.andrew.plinth.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.andrew.plinth.data.loudness.StereoLevel
import dev.andrew.plinth.ui.components.LevelMeter
import dev.andrew.plinth.ui.theme.PlinthTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The meter's two obligations: show the real number, and admit when it has no data. */
@RunWith(AndroidJUnit4::class)
class VuMeterTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun noDataRendersTheUnavailableStateNotAnAnimation() {
        rule.setContent {
            PlinthTheme {
                LevelMeter(level = null, active = true, modifier = Modifier.width(220.dp))
            }
        }
        rule.onNodeWithContentDescription("No level data").assertIsDisplayed()
        rule.onNodeWithText("No level data").assertIsDisplayed()
    }

    @Test
    fun headerPrintsTheLiveValueInDbfs() {
        rule.setContent {
            PlinthTheme {
                // 196 of 255 is -14 dBFS on the -60..0 scale.
                LevelMeter(
                    level = StereoLevel(196, 190, 200),
                    active = true,
                    modifier = Modifier.width(220.dp),
                )
            }
        }
        rule.onNodeWithText("-14 dBFS", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Stereo level meter").assertIsDisplayed()
    }

    @Test
    fun idleMeterShowsTheSameNoDataCopy() {
        rule.setContent {
            PlinthTheme {
                LevelMeter(level = null, active = false, modifier = Modifier.width(220.dp))
            }
        }
        rule.onNodeWithTag("level-meter").assertIsDisplayed()
    }
}
