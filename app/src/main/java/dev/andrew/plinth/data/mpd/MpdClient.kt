package dev.andrew.plinth.data.mpd

import dev.andrew.plinth.data.model.AlbumIdentity
import dev.andrew.plinth.data.model.AudioFormat
import dev.andrew.plinth.data.model.OutputInfo
import dev.andrew.plinth.data.model.PlaybackState
import dev.andrew.plinth.data.model.PlayerState
import dev.andrew.plinth.data.model.QueueItem
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
            queueVersion = s("playlist")?.toIntOrNull() ?: UNKNOWN_VERSION,
            random = s("random").asFlag(),
            repeat = s("repeat").asFlag(),
            single = s("single").asFlag(),
            consume = s("consume").asFlag(),
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
        val albumArtist = s("AlbumArtist")?.ifBlank { artist } ?: artist
        return Track(
            uri = uri,
            albumId = AlbumIdentity.key(albumArtist, artist, s("Album").orEmpty(), format?.channels ?: 2),
            disc = s("Disc")?.toIntOrNull() ?: 1,
            trackNo = s("Track")?.toIntOrNull() ?: 0,
            title = s("Title").orEmpty(),
            artist = artist,
            albumArtist = albumArtist,
            album = s("Album").orEmpty(),
            year = s("Date")?.take(4)?.toIntOrNull(),
            genre = s("Genre"),
            durationSec = duration,
            format = format,
            path = uri.substringBeforeLast('/', ""),
            channels = format?.channels ?: 2,
        )
    }

    /** The whole library in one read: 865 KB / ~90 ms on this streamer. */
    suspend fun listAllInfo(): List<Track> {
        val p = okPairs(socket.command("listallinfo", timeoutMs = MpdSocket.LIST_TIMEOUT_MS))
        return LibraryParser.parse(p)
    }

    /** The queue, whole. Called on every version change rather than diffing. */
    suspend fun playlistInfo(): List<QueueItem> {
        val p = okPairs(socket.command("playlistinfo", timeoutMs = MpdSocket.LIST_TIMEOUT_MS))
        return QueueParser.parse(p)
    }

    /**
     * When MPD last rebuilt its database, in epoch seconds. The loudness scanner
     * records the same value in the envelope header, so comparing the two is the only
     * honest way to say a level file is stale rather than wrong.
     */
    suspend fun dbUpdate(): Long? =
        okPairs(socket.command("stats"))
            .firstOrNull { it.first == "db_update" }
            ?.second
            ?.toLongOrNull()

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

    // ---- writes -------------------------------------------------------------

    suspend fun play(pos: Int? = null) = write(if (pos == null) "play" else "play $pos")

    /** No argument toggles, which is what MPD's own `pause` does. */
    suspend fun pause(next: Boolean? = null) = write(if (next == null) "pause" else "pause ${flag(next)}")

    suspend fun stop() = write("stop")

    suspend fun next() = write("next")

    suspend fun previous() = write("previous")

    suspend fun seekCur(seconds: Double) =
        write("seekcur " + String.format(java.util.Locale.US, "%.2f", seconds))

    suspend fun setRandom(on: Boolean) = write("random ${flag(on)}")

    suspend fun setRepeat(on: Boolean) = write("repeat ${flag(on)}")

    suspend fun setSingle(on: Boolean) = write("single ${flag(on)}")

    suspend fun setConsume(on: Boolean) = write("consume ${flag(on)}")

    suspend fun setCrossfade(seconds: Int) = write("crossfade $seconds")

    // ---- queue --------------------------------------------------------------

    suspend fun add(uri: String, pos: Int? = null) =
        write(if (pos == null) "add ${quote(uri)}" else "add ${quote(uri)} $pos")

    /** Adds and reports the new song id, which is what a "play next" needs. */
    suspend fun addId(uri: String): Int? {
        val p = okPairs(socket.command("addid ${quote(uri)}"))
        return p.firstOrNull { it.first == "Id" }?.second?.toIntOrNull()
    }

    suspend fun deleteId(id: Int) = write("deleteid $id")

    suspend fun moveId(id: Int, to: Int) = write("moveid $id $to")

    suspend fun clearQueue() = write("clear")

    suspend fun savePlaylist(name: String) = write("save ${quote(name)}")

    suspend fun loadPlaylist(name: String) = write("load ${quote(name)}")

    suspend fun listPlaylists(): List<String> =
        okPairs(socket.command("listplaylists")).filter { it.first == "playlist" }.map { it.second }

    /** Raises the server's chunk cap; art needs ~302 KB in one reply. */
    suspend fun binaryLimit(bytes: Int) = write("binarylimit $bytes")

    /** One `albumart` request. Needs `binaryLimit` raised first or it arrives chunked. */
    suspend fun albumArt(uri: String, offset: Int = 0, timeoutMs: Int = MpdSocket.ART_TIMEOUT_MS): BinaryReply =
        socket.commandBinary("albumart ${quote(uri)} $offset", timeoutMs)

    private suspend fun write(line: String) {
        val reply = socket.command(line)
        if (reply is MpdReply.Ack) throw MpdException.Protocol(reply.message)
    }

    private fun flag(on: Boolean) = if (on) 1 else 0

    /** `1` and `oneshot` both mean on; `0` (or absence) means off. */
    private fun String?.asFlag(): Boolean = this != null && this != "0"

    companion object {
        /** MPD omits `playlist`, so a failed parse must not look like version 0. */
        const val UNKNOWN_VERSION = -1

        /** MPD quoting: backslash first, then the quote character. */
        fun quote(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    /** `time: 41:230` → elapsed 41, duration 230. */
    private fun parseTime(time: String?): Pair<Double?, Double?> {
        val parts = time?.split(":") ?: return null to null
        if (parts.size != 2) return null to null
        return parts[0].toDoubleOrNull() to parts[1].toDoubleOrNull()
    }
}
