package dev.andrew.plinth.service

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.andrew.plinth.PlinthApp
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one failure this task exists to prevent: a foreground service that is killed
 * on launch because it never called `startForeground`. The test starts the real
 * service on the device and looks for the real notification, then stops it and
 * watches it go.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    private fun app(): PlinthApp = ApplicationProvider.getApplicationContext()

    @Before
    fun quietTheBackgroundPolicyAndGrantNotifications() {
        // The coordinator would otherwise restart a service the test stopped.
        app().container.playbackService.stop()
        context.stopService(Intent(context, PlaybackService::class.java))
        awaitUntil("service idle before the test") { !PlaybackService.isRunning }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assumeTrue(
            "notifications are a runtime permission on this device",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    @After
    fun stopAndRestartThePolicy() {
        context.stopService(Intent(context, PlaybackService::class.java))
        awaitUntil("service stopped after the test") { !PlaybackService.isRunning }
        app().container.playbackService.start()
    }

    @Test
    fun startingTheServicePostsTheNowPlayingNotification() {
        PlaybackService.start(context)

        awaitUntil("the service to be running") { PlaybackService.isRunning }
        awaitUntil("the now-playing notification") { posted() }
    }

    @Test
    fun stoppingTheServiceTakesTheNotificationAway() {
        PlaybackService.start(context)
        awaitUntil("the service to be running") { PlaybackService.isRunning }
        awaitUntil("the now-playing notification") { posted() }

        PlaybackService.stop(context)

        awaitUntil("the service to stop") { !PlaybackService.isRunning }
        awaitUntil("the notification to be removed") { !posted() }
    }

    private fun posted(): Boolean =
        manager.activeNotifications.any {
            it.notification.channelId == PlaybackService.CHANNEL_ID
        }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("timed out waiting for $what")
    }
}
