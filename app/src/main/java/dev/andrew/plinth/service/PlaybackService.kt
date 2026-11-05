package dev.andrew.plinth.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import dev.andrew.plinth.AppContainer
import dev.andrew.plinth.MainActivity
import dev.andrew.plinth.PlinthApp
import dev.andrew.plinth.R
import dev.andrew.plinth.data.art.ArtResult
import dev.andrew.plinth.data.model.PlaybackState
import dev.andrew.plinth.data.model.PlayerState
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.data.playback.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The foreground half of the design's hybrid background policy: while it runs, the
 * streamer keeps a connection and the notification is the remote control.
 *
 * The order in [onCreate] matters. A `mediaPlayback` service that does not call
 * `startForeground` within a few seconds is killed with a RemoteServiceException,
 * so everything before that call is limited to building the notification — no
 * network, no disk reads, no awaits. This was the trap the previous attempt left
 * unbuilt.
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private lateinit var engine: PlaybackEngine
    private lateinit var session: MediaSessionCompat
    private lateinit var notifications: NotificationManager

    private var shownTrack: Track? = null
    private var shownState: PlayerState? = null
    private var art: Bitmap? = null
    private var artAlbumId: String? = null
    private var artJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        container = (application as PlinthApp).container
        engine = container.engine
        notifications = getSystemService(NotificationManager::class.java)
        createChannel()

        session = MediaSessionCompat(this, SESSION_TAG).apply {
            setCallback(sessionCallback)
            isActive = true
        }

        // Seed from whatever the engine already knows, so a restarted service does
        // not flash a blank notification over a playing track.
        shownTrack = engine.track.value
        shownState = engine.player.value
        publishSessionOnly()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(shownTrack, shownState),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        foreground = true
        isRunning = true

        // A START_STICKY restart can arrive with no activity; the endpoint then has
        // to come from the saved settings rather than the view model.
        scope.launch {
            if (!engine.isConfigured) {
                val settings = container.settings.flow.first()
                engine.connect(settings.host, settings.port)
            }
        }
        engine.setActive(PlaybackEngine.OWNER_SERVICE, true)
        scope.launch { engine.player.collect { onState(it) } }
        scope.launch { engine.track.collect { onTrack(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> engine.play()
            ACTION_PAUSE -> engine.pause()
            ACTION_PLAY_PAUSE -> engine.playPause()
            ACTION_NEXT -> engine.next()
            ACTION_PREVIOUS -> engine.previous()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        foreground = false
        isRunning = false
        scope.cancel()
        engine.setActive(PlaybackEngine.OWNER_SERVICE, false)
        session.isActive = false
        session.release()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun onState(state: PlayerState?) {
        shownState = state
        publish()
    }

    private fun onTrack(track: Track?) {
        shownTrack = track
        if (track != null && track.albumId != artAlbumId) loadArt(track)
        publish()
    }

    private fun publish() {
        if (!foreground) return
        publishSessionOnly()
        notifications.notify(NOTIFICATION_ID, buildNotification(shownTrack, shownState))
        updateWakeLock(shownState?.state)
    }

    private fun publishSessionOnly() {
        session.setMetadata(metadataFor(shownTrack))
        session.setPlaybackState(playbackStateFor(shownState))
    }

    /** Art is fetched at most once per album; the UI shares the same cache file. */
    private fun loadArt(track: Track) {
        if (track.albumId.isBlank()) return
        artJob?.cancel()
        artJob = scope.launch {
            val albumId = track.albumId
            val bitmap = withContext(Dispatchers.IO) {
                container.cachedArt(albumId)?.let { decode(it) }
                    ?: fetchArt(track)?.let { decode(it) }
            }
            // The track may have changed again while the fetch was in flight.
            if (bitmap != null && shownTrack?.albumId == albumId) {
                art = bitmap
                artAlbumId = albumId
                publish()
            }
        }
    }

    private suspend fun fetchArt(track: Track): ByteArray? {
        val result = runCatching {
            engine.withClient { container.artFor(it).forAlbum(track.albumId, track.uri) }
        }.getOrNull()
        return (result as? ArtResult.Jpeg)?.bytes
    }

    private fun decode(file: File): Bitmap? = runCatching {
        decodeWith { BitmapFactory.decodeFile(file.absolutePath, it) }
    }.getOrNull()

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        decodeWith { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
    }.getOrNull()

    /** One downscale pass for the notification: the cached cover is up to 1280². */
    private fun decodeWith(decoder: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decoder(bounds)
        var sample = 1
        while (bounds.outWidth > 0 && bounds.outWidth / (sample * 2) >= ART_PX) sample *= 2
        return decoder(BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun buildNotification(track: Track?, state: PlayerState?): Notification {
        val playing = state?.state == PlaybackState.Play
        val title = track?.let { it.title.ifBlank { it.uri.substringAfterLast('/') } }
            ?: "Connecting…"
        val subtitle = track?.let { t ->
            listOf(t.artist.ifBlank { t.albumArtist }, t.album)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }.orEmpty()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_plinth)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(actionIntent(ACTION_STOP, 99))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(playing)
            .addAction(
                action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS, 1),
            )
            .addAction(
                if (playing) action(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE, 2)
                else action(android.R.drawable.ic_media_play, "Play", ACTION_PLAY, 2),
            )
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT, 3))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP, 4))

        art?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun metadataFor(track: Track?): MediaMetadataCompat {
        val title = track?.let { it.title.ifBlank { it.uri.substringAfterLast('/') } }.orEmpty()
        val artist = track?.let { it.artist.ifBlank { it.albumArtist } }.orEmpty()
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track?.album.orEmpty())
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                ((track?.durationSec ?: 0) * 1000).toLong(),
            )
        art?.let { builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
        return builder.build()
    }

    private fun playbackStateFor(state: PlayerState?): PlaybackStateCompat {
        val playback = when (state?.state) {
            PlaybackState.Play -> PlaybackStateCompat.STATE_PLAYING
            PlaybackState.Pause -> PlaybackStateCompat.STATE_PAUSED
            PlaybackState.Stop -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_NONE
        }
        val positionMs = ((state?.elapsed ?: 0.0) * 1000).toLong()
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO,
            )
            .setState(playback, positionMs, if (playback == PlaybackStateCompat.STATE_PLAYING) 1f else 0f)
            .build()
    }

    private fun action(icon: Int, title: String, action: String, requestCode: Int): NotificationCompat.Action =
        NotificationCompat.Action(icon, title, actionIntent(action, requestCode))

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                // Reopening must return to the live activity, not stack a second one
                // with its own ViewModel, tickers and engine owner.
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Now playing",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Transport controls for the streamer"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notifications.createNotificationChannel(channel)
    }

    /** A CPU wakelock only while the streamer is playing; paused lets the tablet doze. */
    private fun updateWakeLock(state: PlaybackState?) {
        if (state == PlaybackState.Play) {
            if (wakeLock?.isHeld == true) return
            val manager = getSystemService(PowerManager::class.java)
            wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "plinth:playback").apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_MS)
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = engine.play()

        override fun onPause() = engine.pause()

        override fun onSkipToNext() = engine.next()

        override fun onSkipToPrevious() = engine.previous()

        override fun onSeekTo(pos: Long) = engine.seek(pos / 1000.0)

        override fun onStop() = engine.stop()
    }

    companion object {
        const val CHANNEL_ID = "now_playing"

        const val ACTION_PLAY = "dev.andrew.plinth.action.PLAY"
        const val ACTION_PAUSE = "dev.andrew.plinth.action.PAUSE"
        const val ACTION_PLAY_PAUSE = "dev.andrew.plinth.action.PLAY_PAUSE"
        const val ACTION_NEXT = "dev.andrew.plinth.action.NEXT"
        const val ACTION_PREVIOUS = "dev.andrew.plinth.action.PREVIOUS"
        const val ACTION_STOP = "dev.andrew.plinth.action.STOP"

        private const val NOTIFICATION_ID = 1
        private const val SESSION_TAG = "Plinth"
        private const val ART_PX = 512
        private const val WAKE_LOCK_MS = 10 * 60_000L

        /** Set for the service's lifetime; the controller reads it without binding. */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
