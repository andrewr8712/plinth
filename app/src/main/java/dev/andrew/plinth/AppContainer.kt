package dev.andrew.plinth

import android.content.Context
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.data.lib.LibraryDb
import dev.andrew.plinth.data.lib.LibraryRepository
import dev.andrew.plinth.data.mpd.MpdClient
import dev.andrew.plinth.data.mpd.MpdConnection
import dev.andrew.plinth.data.settings.SettingsStore
import java.io.File

/** Everything long-lived, built once in [PlinthApp]. No Android framework types in the UI. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings = SettingsStore(appContext)

    private val db = LibraryDb.build(appContext)
    val library = LibraryRepository(db)

    fun connection(host: String, port: Int) = MpdConnection(host, port)

    fun artFor(client: MpdClient) = ArtRepository(client, File(appContext.cacheDir, "covers"))
}
