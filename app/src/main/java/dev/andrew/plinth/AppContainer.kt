package dev.andrew.plinth

import android.content.Context
import android.os.BatteryManager
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.data.lib.LibraryDb
import dev.andrew.plinth.data.lib.LibraryRepository
import dev.andrew.plinth.data.mpd.MpdClient
import dev.andrew.plinth.data.playback.PlaybackEngine
import dev.andrew.plinth.data.settings.SettingsStore
import dev.andrew.plinth.service.PlaybackServiceController
import java.io.File

/** Everything long-lived, built once in [PlinthApp]. No Android framework types in the UI. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings = SettingsStore(appContext)

    private val db = LibraryDb.build(appContext)
    val library = LibraryRepository(db)

    val loudness = dev.andrew.plinth.data.loudness.LoudnessImporter(
        File(appContext.filesDir, "").apply { mkdirs() },
    )

    /** The socket owner, shared by the UI and the playback service. */
    val engine = PlaybackEngine()

    /** Decides when the service runs, from the policy in [PlaybackEngine]'s state. */
    val playbackService = PlaybackServiceController(appContext, engine, settings) { isCharging() }

    fun artFor(client: MpdClient) = ArtRepository(client, File(appContext.cacheDir, "covers"))

    /** The cached cover for a notification; the UI may already have fetched it. */
    fun cachedArt(albumId: String): File? =
        File(File(appContext.cacheDir, "covers/art"), "$albumId.jpg")
            .takeIf { it.isFile && it.length() > 0 }

    /** BatteryManager is cheap to query; the policy runs every couple of seconds. */
    fun isCharging(): Boolean =
        (appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.isCharging == true
}
