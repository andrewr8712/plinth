package dev.andrew.plinth.data.settings

/**
 * Everything the app remembers between launches. Defaults point at the streamer,
 * so a fresh install connects without typing anything.
 */
data class Settings(
    val host: String = "10.0.0.219",
    val port: Int = 6600,
    val stayAwakeWhileCharging: Boolean = false,
    val meterOffsetPcmMs: Int = 120,
    val meterOffsetDsdMs: Int = 240,
)
