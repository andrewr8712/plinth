package dev.andrew.plinth.service

import dev.andrew.plinth.data.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The coordinator's rules, without an Android device: when a foreground service is
 * summoned, when it is left alone, and when it is put away.
 */
class PlaybackServiceControllerTest {

    private fun action(
        state: PlaybackState,
        running: Boolean = false,
        stayAwake: Boolean = false,
        charging: Boolean = false,
        minutes: Long = 0,
    ) = serviceAction(state, stayAwake, charging, running, minutes)

    @Test
    fun `playing starts the service and leaves it alone once up`() {
        assertEquals(ServiceAction.Start, action(PlaybackState.Play, running = false))
        assertEquals(ServiceAction.None, action(PlaybackState.Play, running = true))
    }

    @Test
    fun `a paused streamer does not summon a service, but keeps one for a minute`() {
        assertEquals(ServiceAction.None, action(PlaybackState.Pause, running = false))
        assertEquals(ServiceAction.None, action(PlaybackState.Pause, running = true, minutes = 0))
        assertEquals(ServiceAction.None, action(PlaybackState.Pause, running = true, minutes = 1))
        assertEquals(ServiceAction.Stop, action(PlaybackState.Pause, running = true, minutes = 2))
    }

    @Test
    fun `a stopped streamer never starts one`() {
        assertEquals(ServiceAction.None, action(PlaybackState.Stop, running = false, minutes = 5))
        assertEquals(ServiceAction.Stop, action(PlaybackState.Stop, running = true, minutes = 5))
    }

    @Test
    fun `stay awake on a charger is the one reason to run while idle`() {
        assertEquals(
            ServiceAction.Start,
            action(PlaybackState.Stop, running = false, stayAwake = true, charging = true),
        )
        assertEquals(
            ServiceAction.None,
            action(
                PlaybackState.Stop,
                running = true,
                stayAwake = true,
                charging = true,
                minutes = 60,
            ),
        )
        assertEquals(
            "the setting without a charger changes nothing",
            ServiceAction.None,
            action(PlaybackState.Stop, running = false, stayAwake = true, charging = false),
        )
    }

    @Test
    fun `an unknown state is idle, and idle does not start a service`() {
        assertEquals(ServiceAction.None, action(PlaybackState.Unknown, running = false))
        assertEquals(ServiceAction.Stop, action(PlaybackState.Unknown, running = true, minutes = 2))
    }
}
