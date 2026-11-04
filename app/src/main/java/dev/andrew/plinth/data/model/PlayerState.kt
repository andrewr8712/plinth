package dev.andrew.plinth.data.model

/** What MPD is doing, as it reports it. */
enum class PlaybackState { Play, Pause, Stop, Unknown }

/**
 * The audio format MPD reports as `rate:bits:channels`, or `dsdN:channels` for DSD.
 * Malformed or absent values are `null` — never a guess.
 */
data class AudioFormat(
    val sampleRate: Int,
    val bits: Int,
    val channels: Int,
    val isDsd: Boolean,
    val dsdRate: Int?,
) {
    companion object {
        fun parse(s: String?): AudioFormat? {
            val raw = s?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val parts = raw.split(":")
            val head = parts.first()

            // DSD is reported as `dsdN:channels` — two fields, not three.
            if (head.startsWith("dsd")) {
                if (parts.size < 2) return null
                val rate = head.removePrefix("dsd").toIntOrNull() ?: return null
                val channels = parts[1].toIntOrNull() ?: return null
                return AudioFormat(
                    sampleRate = rate * 44_100,
                    bits = 1,
                    channels = channels,
                    isDsd = true,
                    dsdRate = rate,
                )
            }

            if (parts.size < 3) return null
            val channels = parts.last().toIntOrNull() ?: return null
            val sampleRate = head.toIntOrNull() ?: return null
            val bits = parts[1].toIntOrNull() ?: return null
            return AudioFormat(sampleRate, bits, channels, isDsd = false, dsdRate = null)
        }
    }
}

/** One snapshot of `status`. Every field is optional because MPD omits what it does not know. */
data class PlayerState(
    val state: PlaybackState,
    val songId: Int?,
    val songPos: Int?,
    val elapsed: Double?,
    val duration: Double?,
    val queueVersion: Int,
    val random: Boolean,
    val repeat: Boolean,
    val single: Boolean,
    val consume: Boolean,
    val format: AudioFormat?,
    val bitrateKbps: Int?,
    val audioOutRateHz: Int?,
    val replayGainMode: String?,
)
