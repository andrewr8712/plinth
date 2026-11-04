package dev.andrew.plinth.data.model

import java.security.MessageDigest

/** One album after grouping: the unit the grid and the now-playing spine show. */
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val trackCount: Int,
    val edition: String?,
)

/**
 * Decides when two folders are the same album. The rules come from this library's
 * reality: duplicate rips in two folders, box sets split across five, and surround
 * mixes that share a title but are a different record.
 */
object AlbumIdentity {

    fun normalise(s: String): String = s.trim()
        .lowercase()
        .replace(WHITESPACE, " ")
        .removeSuffix("*")
        .trim()

    /** null for stereo; a label for everything wider, so 5.1 is never "the album". */
    fun edition(channels: Int): String? = when {
        channels <= 2 -> null
        channels == 4 -> "4.0"
        channels == 6 -> "5.1"
        channels == 8 -> "7.1"
        else -> "${channels}ch"
    }

    /**
     * Album identity. Empty `AlbumArtist` falls back to `Artist`; the folder name is
     * never part of the key, which is what lets a box set merge.
     */
    fun key(albumArtist: String, artist: String, album: String, channels: Int): String {
        val who = normalise(albumArtist.ifBlank { artist })
        val payload = who + SEP + normalise(album) + SEP + (edition(channels) ?: "")
        return sha1Hex(payload).take(ID_LENGTH)
    }

    /**
     * Drops repeats of (disc, track, title, duration) *within one album*, keeping the
     * first copy. The album id is part of the key on purpose: this library reuses a
     * title and duration across albums (a single and its parent record, the stereo and
     * binaural mixes of one album), and dropping those left albums holding a fraction
     * of their tracks. Two copies of the same rip share an album id, so they still
     * collapse.
     */
    fun dedupe(tracks: List<Track>): List<Track> {
        val seen = HashSet<String>()
        return tracks.filter {
            seen.add("${it.albumId}|${it.disc}|${it.trackNo}|${it.title}|${it.durationSec}")
        }
    }

    private fun sha1Hex(s: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private val WHITESPACE = Regex("\\s+")
    private const val SEP = "\u0000"
    private const val ID_LENGTH = 16
}
