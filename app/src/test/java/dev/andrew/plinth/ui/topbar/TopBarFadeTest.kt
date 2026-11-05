package dev.andrew.plinth.ui.topbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopBarFadeTest {

    @Test
    fun `the bar is fully opaque before the idle threshold`() {
        // Entering a route or touching anything resets the idle clock to zero.
        assertEquals(TopBarFade.FULL_ALPHA, topBarAlphaAfter(0L), 0f)
        assertEquals(TopBarFade.FULL_ALPHA, topBarAlphaAfter(3_999L), 0f)
    }

    @Test
    fun `the bar rests low once the idle threshold is reached`() {
        assertEquals(TopBarFade.RESTING_ALPHA, topBarAlphaAfter(4_000L), 0f)
        assertEquals(TopBarFade.RESTING_ALPHA, topBarAlphaAfter(60_000L), 0f)
    }

    @Test
    fun `a reset after idling returns to full opacity`() {
        // Idle past the threshold: the bar has faded.
        val rested = topBarAlphaAfter(TopBarFade.IDLE_THRESHOLD_MS)
        assertEquals(TopBarFade.RESTING_ALPHA, rested, 0f)

        // An interaction restarts the idle clock at zero, so the bar is opaque again.
        assertEquals(TopBarFade.FULL_ALPHA, topBarAlphaAfter(0L), 0f)
        assertTrue(rested < topBarAlphaAfter(0L))
    }

    @Test
    fun `the resting alpha stays in the legible band`() {
        assertTrue(TopBarFade.RESTING_ALPHA in 0.18f..0.25f)
        assertTrue(TopBarFade.FADE_MILLIS in 400..600)
    }
}
