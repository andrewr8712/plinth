package dev.andrew.plinth.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Preferences-backed settings. Not proto: there are five values and no schema to evolve. */
class SettingsStore(
    context: Context,
    fileName: String = DEFAULT_FILE,
) {

    private val appContext = context.applicationContext

    // DataStore permits exactly one active instance per file, so two SettingsStore
    // objects for the same file must share one store rather than race for it.
    private val store: DataStore<Preferences> by lazy {
        val file = appContext.preferencesDataStoreFile(fileName)
        synchronized(ACTIVE) {
            ACTIVE.getOrPut(file.absolutePath) {
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(produceFile = { file })
            }
        }
    }

    val flow: Flow<Settings> = store.data.map { it.read() }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val next = transform(prefs.read())
            prefs[KEY_HOST] = next.host
            prefs[KEY_PORT] = next.port
            prefs[KEY_STAY_AWAKE] = next.stayAwakeWhileCharging
            prefs[KEY_METER_PCM] = next.meterOffsetPcmMs
            prefs[KEY_METER_DSD] = next.meterOffsetDsdMs
        }
    }

    private fun Preferences.read() = Settings(
        host = this[KEY_HOST] ?: Settings().host,
        port = this[KEY_PORT] ?: Settings().port,
        stayAwakeWhileCharging = this[KEY_STAY_AWAKE] ?: false,
        meterOffsetPcmMs = this[KEY_METER_PCM] ?: Settings().meterOffsetPcmMs,
        meterOffsetDsdMs = this[KEY_METER_DSD] ?: Settings().meterOffsetDsdMs,
    )

    private companion object {
        val ACTIVE = HashMap<String, DataStore<Preferences>>()
        const val DEFAULT_FILE = "plinth_settings"
        val KEY_HOST = stringPreferencesKey("host")
        val KEY_PORT = intPreferencesKey("port")
        val KEY_STAY_AWAKE = booleanPreferencesKey("stay_awake")
        val KEY_METER_PCM = intPreferencesKey("meter_offset_pcm")
        val KEY_METER_DSD = intPreferencesKey("meter_offset_dsd")
    }
}
