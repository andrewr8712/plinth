package dev.andrew.plinth.ui.components

import dev.andrew.plinth.data.model.SignalVerdicts
import dev.andrew.plinth.data.model.Track

/** One label/value line in the details sheet. */
data class DetailRow(val label: String, val value: String)

private val TAG_LABELS = mapOf(
    "Title" to "Title",
    "Artist" to "Artist",
    "AlbumArtist" to "Album artist",
    "Album" to "Album",
    "Date" to "Date",
    "Genre" to "Genre",
    "Disc" to "Disc",
    "Track" to "Track",
)

/** Read order for the tags everyone recognises; anything else follows in server order. */
private val TAG_ORDER = listOf("Title", "Artist", "AlbumArtist", "Album", "Date", "Genre", "Disc", "Track")

/**
 * These keys describe the file rather than tag it, and the File section already carries
 * them. `Time` and `duration` are the same fact twice.
 */
private val FILE_KEYS = setOf("file", "Last-Modified", "Format", "Time", "duration")

fun tagLabel(key: String): String = TAG_LABELS[key] ?: key

/** The format line for a file: FLAC · 24/96 · 2 ch, DSD64 · 2 ch. */
fun formatDetail(track: Track): String {
    val label = SignalVerdicts.labelFor(track.format, track.uri)
    if (label == "—") return "—"
    val channels = track.format?.channels ?: track.channels
    return "$label · $channels ch"
}

/**
 * The File section. [tags] is the raw `lsinfo` reply when it arrived; Last-Modified is
 * only reported there, and its absence just means the row is not shown.
 */
fun fileDetailRows(track: Track, tags: List<Pair<String, String>>?): List<DetailRow> {
    val modified = tags?.firstOrNull { it.first == "Last-Modified" }?.second
    return buildList {
        add(DetailRow("Name", track.uri.substringAfterLast('/')))
        add(DetailRow("Path", track.path.ifBlank { "—" }))
        add(DetailRow("Format", formatDetail(track)))
        add(DetailRow("Length", clock(track.durationSec)))
        if (!modified.isNullOrBlank()) add(DetailRow("Modified", modified))
    }
}

/**
 * Every tag the streamer reported, minus the file facts above. Multi-value tags (two
 * Artist lines, say) are joined rather than repeated, and the familiar tags are pulled
 * to the top so the list opens on Title/Artist/Album instead of whatever order the file
 * happens to store them in.
 */
fun tagDetailRows(tags: List<Pair<String, String>>): List<DetailRow> {
    val values = linkedMapOf<String, MutableList<String>>()
    for ((key, value) in tags) {
        if (key in FILE_KEYS || value.isBlank()) continue
        values.getOrPut(key) { mutableListOf() }.add(value)
    }
    val ordered = TAG_ORDER.filter { it in values } + values.keys.filterNot { it in TAG_ORDER }
    return ordered.map { key -> DetailRow(tagLabel(key), values.getValue(key).joinToString(" · ")) }
}

/** What the Tags section falls back to when the streamer cannot answer. */
fun cachedTagRows(track: Track): List<DetailRow> = buildList {
    add(DetailRow("Title", track.title))
    add(DetailRow("Artist", track.artist))
    if (track.albumArtist.isNotBlank() && track.albumArtist != track.artist) {
        add(DetailRow("Album artist", track.albumArtist))
    }
    add(DetailRow("Album", track.album))
    track.year?.let { add(DetailRow("Date", it.toString())) }
    track.genre?.takeIf { it.isNotBlank() }?.let { add(DetailRow("Genre", it)) }
    if (track.disc > 1) add(DetailRow("Disc", track.disc.toString()))
    if (track.trackNo > 0) add(DetailRow("Track", track.trackNo.toString()))
}.filter { it.value.isNotBlank() }
