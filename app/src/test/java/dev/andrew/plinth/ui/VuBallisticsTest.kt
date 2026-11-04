package dev.andrew.plinth.ui

import dev.andrew.plinth.ui.components.Ballistics
import org.junit.Assert.assertTrue
import org.junit.Test

/** The meter's feel: it must jump to a transient and fall away like a needle. */
class VuBallisticsTest {

    @Test
    fun `attack is faster than release`() {
        val up = Ballistics().step(0f, 1f, 30)
        val down = Ballistics(initial = 1f).step(1f, 0f, 30)
        assertTrue("attack should be most of the way up in 30 ms, was $up", up > 0.5f)
        assertTrue("release should barely move in 30 ms, was $down", down > 0.85f)
    }

    @Test
    fun `release reaches near zero after a second of silence`() {
        val ballistics = Ballistics(initial = 1f)
        var value = 1f
        var elapsed = 0L
        while (elapsed < 1_000) {
            value = ballistics.step(value, 0f, 33)
            elapsed += 33
        }
        assertTrue("after a second the bar should be under 0.1, was $value", value < 0.1f)
    }

    @Test
    fun `attack converges upward within a couple of hundred milliseconds`() {
        val ballistics = Ballistics()
        var value = 0f
        var elapsed = 0L
        while (elapsed < 200) {
            value = ballistics.step(value, 1f, 33)
            elapsed += 33
        }
        assertTrue("attack should be over 0.95 within 200 ms, was $value", value > 0.95f)
    }

    @Test
    fun `a zero-length step changes nothing`() {
        val ballistics = Ballistics(initial = 0.4f)
        assertTrue(ballistics.step(0.4f, 1f, 0) == 0.4f)
    }
}
