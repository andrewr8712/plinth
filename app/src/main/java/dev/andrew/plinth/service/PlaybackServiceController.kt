package dev.andrew.plinth.service

import android.content.Context
import android.os.SystemClock
import dev.andrew.plinth.data.model.BackgroundPolicy
import dev.andrew.plinth.data.model.PlaybackState
import dev.andrew.plinth.data.playback.PlaybackEngine
import dev.andrew.plinth.data.settings.Settings
import dev.andrew.plinth.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the coordinator should do with the service on one evaluation. */
enum class ServiceAction { None, Start, Stop }

/**
 * The start/stop rules, kept free of Android so they can be tested on the JVM.
 *
 * Starting and keeping are deliberately different questions. A paused streamer on
 * app launch must not summon a foreground service merely to sit out a grace period;
 * a service that is already up when the user pauses should survive that minute.
 */
fun serviceAction(
    state: PlaybackState,
    stayAwakeWhileCharging: Boolean,
    charging: Boolean,
    running: Boolean,
    minutesSinceInactive: Long,
): ServiceAction {
    val shouldStart = state == PlaybackState.Play || (stayAwakeWhileCharging && charging)
    val shouldKeep = BackgroundPolicy(stayAwakeWhileCharging, charging)
        .shouldRun(state, minutesSinceInactive)
    return when {
        shouldStart && !running -> ServiceAction.Start
        !shouldKeep && running -> ServiceAction.Stop
        else -> ServiceAction.None
    }
}

/**
 * Watches the engine and moves [PlaybackService] in and out of the foreground.
 *
 * It lives in application scope rather than the activity so that a playing tablet
 * keeps its service when the task is backgrounded; once the service is up it is the
 * service's own demand that keeps the engine polling, and the coordinator merely
 * decides when the minute of grace has run out.
 */
class PlaybackServiceController(
    private val context: Context,
    private val engine: PlaybackEngine,
    private val settingsStore: SettingsStore,
    private val charging: () -> Boolean,
) {

    private var scope = newScope()
    private var settings = Settings()
    private var lastState: PlaybackState? = null
    private var inactiveSince = SystemClock.elapsedRealtime()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            settingsStore.flow.collect {
                settings = it
                evaluate()
            }
        }
        scope.launch { engine.player.collect { evaluate() } }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                evaluate()
            }
        }
    }

    /** Stops watching; used by tests that need the real service, and by symmetry. */
    fun stop() {
        started = false
        scope.cancel()
        scope = newScope()
    }

    @Synchronized
    private fun evaluate() {
        val state = engine.player.value?.state ?: return
        val now = SystemClock.elapsedRealtime()
        if (state != lastState) {
            lastState = state
            if (state != PlaybackState.Play) inactiveSince = now
        }
        if (state == PlaybackState.Play) inactiveSince = now
        val minutes = (now - inactiveSince) / 60_000L
        when (
            serviceAction(
                state = state,
                stayAwakeWhileCharging = settings.stayAwakeWhileCharging,
                charging = charging(),
                running = PlaybackService.isRunning,
                minutesSinceInactive = minutes,
            )
        ) {
            ServiceAction.Start -> runCatching { PlaybackService.start(context) }
            ServiceAction.Stop -> PlaybackService.stop(context)
            ServiceAction.None -> Unit
        }
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private companion object {
        const val TICK_MS = 5_000L
    }
}
