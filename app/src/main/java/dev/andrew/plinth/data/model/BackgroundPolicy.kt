package dev.andrew.plinth.data.model

/**
 * Whether the app should be holding a connection in the background.
 *
 * The tablet is an appliance while it is playing and an app otherwise: playing keeps
 * the service and the socket up; paused or stopped keeps them for a minute, so a quick
 * pause-and-resume does not tear the socket down and re-dial it.
 */
class BackgroundPolicy(
    private val stayAwakeWhileCharging: Boolean,
    private val charging: Boolean,
) {

    /** [minutesSinceInactive] is how long the streamer has been paused or stopped. */
    fun shouldRun(state: PlaybackState, minutesSinceInactive: Long): Boolean = when {
        state == PlaybackState.Play -> true
        stayAwakeWhileCharging && charging -> true
        else -> minutesSinceInactive <= GRACE_MINUTES
    }

    /**
     * The screen stays lit for something worth watching: music that is playing, or —
     * when the tablet is used as an appliance — any state at all while the charger is
     * attached and the setting says to stay awake.
     */
    fun shouldKeepScreenOn(state: PlaybackState): Boolean =
        state == PlaybackState.Play || (stayAwakeWhileCharging && charging)

    private companion object {
        const val GRACE_MINUTES = 1L
    }
}
