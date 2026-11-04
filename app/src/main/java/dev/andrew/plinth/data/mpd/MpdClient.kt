package dev.andrew.plinth.data.mpd

import dev.andrew.plinth.data.model.AudioFormat
import dev.andrew.plinth.data.model.OutputInfo
import dev.andrew.plinth.data.model.PlaybackState
import dev.andrew.plinth.data.model.PlayerState
import dev.andrew.plinth.data.model.Track

/** Read side of the MPD protocol. Writes arrive in the next task. */
class MpdClient(private val socket: MpdSocket) {

    suspend fun status(): PlayerState {
        val p = okPairs(socket.command("status"))
        fun s(key: String): String? = p.firstOrNull { it.first == key }?.second
        val audio = AudioFormat.parse(s("audio"))
        val (elapsedFromTime, durationFromTime) = parseTime(s("time"))
        return PlayerState(
            state = when (s("state")) {
                "play" -> PlaybackState.Play
                "pause" -> PlaybackState.Pause
                "stop" -> PlaybackState.Stop
                else -> PlaybackState.Unknown
            },
            songId = s("songid")?.toIntOrNull(),
            songPos = s("song")?.toIntOrNull(),
            elapsed = s("elapsed")?.toDoubleOrNull() ?: elapsedFromTime,
            duration = s("duration")?.toDoubleOrNull() ?: durationFromTime,
            queueVersion = s("playlist")?.toIntOrNull() ?: 0,
            random = s("random") == "1",
            repeat = s("repeat") == "1",
            single = s("single") == "1",
            consume = s("consume") == "1",
            format = audio,
            bitrateKbps = s("bitrate")?.toIntOrNull(),
            audioOutRateHz = audio?.sampleRate,
            replayGainMode = s("replay_gain_mode"),
        )
    }

    suspend fun currentSong(): Track? {
        val p = okPairs(socket.command("currentsong"))
        if (p.isEmpty()) return null
        fun s(key: String): String? = p.firstOrNull { it.first == key }?.second
        val uri = s("file") ?: return null
        val format = AudioFormat.parse(s("Format"))
        val duration = s("duration")?.toDoubleOrNull()?.toInt()
            ?: s("Time")?.toIntOrNull()
            ?: 0
        val artist = s("Artist").orEmpty()
        return Track(
            uri = uri,
            albumId = "", // filled by AlbumIdentity in Task 6
            disc = s("Disc")?.toIntOrNull() ?: 1,
            trackNo = s("Track")?.toIntOrNull() ?: 0,
            title = s("Title").orEmpty(),
            artist = artist,
            albumArtist = s("AlbumArtist")?.ifBlank { artist } ?: artist,
            album = s("Album").orEmpty(),
            year = s("Date")?.take(4)?.toIntOrNull(),
            genre = s("Genre"),
            durationSec = duration,
            format = format,
            path = uri.substringBeforeLast('/', ""),
            channels = format?.channels ?: 2,
        )
    }

    suspend fun outputs(): List<OutputInfo> {
        val p = okPairs(socket.command("outputs"))
        val out = ArrayList<OutputInfo>()
        var id: Int? = null
        var name = ""
        var enabled = false
        var dop = false

        fun flush() {
            id?.let { out += OutputInfo(it, name, enabled, dop) }
            name = ""
            enabled = false
            dop = false
        }

        for ((key, value) in p) {
            when (key) {
                "outputid" -> {
                    flush()
                    id = value.toIntOrNull()
                }
                "outputname" -> name = value
                "outputenabled" -> enabled = value == "1"
                "attribute" -> if (value.startsWith("dop=")) dop = value.removePrefix("dop=") == "1"
            }
        }
        flush()
        return out
    }

    private fun okPairs(reply: MpdReply): List<Pair<String, String>> = when (reply) {
        is MpdReply.Ok -> reply.pairs
        is MpdReply.Ack -> throw MpdException.Protocol(reply.message)
    }

    /** `time: 41:230` → elapsed 41, duration 230. */
    private fun parseTime(time: String?): Pair<Double?, Double?> {
        val parts = time?.split(":") ?: return null to null
        if (parts.size != 2) return null to null
        return parts[0].toDoubleOrNull() to parts[1].toDoubleOrNull()
    }
}
