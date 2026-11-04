package dev.andrew.plinth.data.mpd

import dev.andrew.plinth.data.model.AlbumIdentity
import dev.andrew.plinth.data.model.AudioFormat
import dev.andrew.plinth.data.model.Track

/**
 * Turns `listallinfo` into tracks. There are no blank lines between songs: a block
 * starts at `file:` and runs until the next one, so that is the only separator.
 */
object LibraryParser {

    fun parse(pairs: List<Pair<String, String>>, fallbackFormatFromExt: Boolean = true): List<Track> {
        val out = ArrayList<Track>()
        var block: MutableList<Pair<String, String>>? = null

        fun flush() {
            block?.let { out += toTrack(it, fallbackFormatFromExt) }
            block = null
        }

        for (pair in pairs) {
            if (pair.first == "file") {
                flush()
                block = arrayListOf(pair)
            } else {
                block?.add(pair)
            }
        }
        flush()
        return out
    }

    private fun toTrack(pairs: List<Pair<String, String>>, fallbackFormatFromExt: Boolean): Track {
        fun t(key: String): String? = pairs.firstOrNull { it.first == key }?.second

        val uri = t("file").orEmpty()
        val artist = t("Artist").orEmpty()
        val albumArtist = t("AlbumArtist")?.ifBlank { artist } ?: artist
        val album = t("Album").orEmpty()
        val format = AudioFormat.parse(t("Format"))
            ?: if (fallbackFormatFromExt) formatFromExtension(uri) else null
        val channels = format?.channels ?: 2

        return Track(
            uri = uri,
            albumId = AlbumIdentity.key(albumArtist, artist, album, channels),
            disc = t("Disc")?.toIntOrNull() ?: 1,
            trackNo = t("Track")?.toIntOrNull() ?: 0,
            title = t("Title")?.ifBlank { null } ?: fileName(uri),
            artist = artist,
            albumArtist = albumArtist,
            album = album,
            year = t("Date")?.take(4)?.toIntOrNull(),
            genre = t("Genre"),
            durationSec = t("Time")?.toIntOrNull()
                ?: t("duration")?.toDoubleOrNull()?.toInt()
                ?: 0,
            format = format,
            path = uri.substringBeforeLast('/', ""),
            channels = channels,
        )
    }

    /** SACD rips carry no `Format:`; the extension is the only evidence of DSD. */
    private fun formatFromExtension(uri: String): AudioFormat? {
        val lower = uri.lowercase()
        return when {
            lower.endsWith(".dsf") || lower.endsWith(".dff") ->
                AudioFormat(sampleRate = 2_822_400, bits = 1, channels = 2, isDsd = true, dsdRate = 64)
            else -> null
        }
    }

    private fun fileName(uri: String): String =
        uri.substringAfterLast('/').substringBeforeLast('.')
}
