package dev.andrew.plinth.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPolicyTest {

    private val plain = BackgroundPolicy(stayAwakeWhileCharging = false, charging = false)

    @Test
    fun `keeps running while playing and for a minute after`() {
        assertTrue(plain.shouldRun(PlaybackState.Play, minutesSinceInactive = 999))
        assertTrue(plain.shouldRun(PlaybackState.Pause, minutesSinceInactive = 0))
        assertTrue("a minute of grace is the point of the grace", plain.shouldRun(PlaybackState.Pause, 1))
        assertFalse(plain.shouldRun(PlaybackState.Pause, minutesSinceInactive = 2))
        assertFalse(plain.shouldRun(PlaybackState.Stop, minutesSinceInactive = 5))
    }

    @Test
    fun `stay awake while charging keeps it alive when idle`() {
        assertTrue(
            BackgroundPolicy(stayAwakeWhileCharging = true, charging = true)
                .shouldRun(PlaybackState.Stop, minutesSinceInactive = 60),
        )
        assertFalse(
            "the setting without a charger changes nothing",
            BackgroundPolicy(stayAwakeWhileCharging = true, charging = false)
                .shouldRun(PlaybackState.Stop, minutesSinceInactive = 60),
        )
        assertFalse(
            "charging without the setting changes nothing",
            BackgroundPolicy(stayAwakeWhileCharging = false, charging = true)
                .shouldRun(PlaybackState.Stop, minutesSinceInactive = 60),
        )
    }

    @Test
    fun `screen stays on only while playing`() {
        assertTrue(plain.shouldKeepScreenOn(PlaybackState.Play))
        assertFalse(plain.shouldKeepScreenOn(PlaybackState.Pause))
        assertFalse(plain.shouldKeepScreenOn(PlaybackState.Stop))
    }

    @Test
    fun `stay awake while charging lights the screen even when stopped`() {
        val awake = BackgroundPolicy(stayAwakeWhileCharging = true, charging = true)
        assertTrue(awake.shouldKeepScreenOn(PlaybackState.Pause))
        assertTrue(awake.shouldKeepScreenOn(PlaybackState.Stop))
        assertFalse(
            "the setting without a charger still sleeps the screen",
            BackgroundPolicy(stayAwakeWhileCharging = true, charging = false)
                .shouldKeepScreenOn(PlaybackState.Stop),
        )
    }

    @Test
    fun `unknown state is treated as idle, not as playing`() {
        assertTrue(plain.shouldRun(PlaybackState.Unknown, minutesSinceInactive = 0))
        assertFalse(plain.shouldRun(PlaybackState.Unknown, minutesSinceInactive = 3))
        assertFalse(plain.shouldKeepScreenOn(PlaybackState.Unknown))
    }
}
