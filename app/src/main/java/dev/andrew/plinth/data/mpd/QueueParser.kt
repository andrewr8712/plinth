package dev.andrew.plinth.data.mpd

import dev.andrew.plinth.data.model.QueueItem

/** Turns `playlistinfo` into queue rows. Blocks start at `file:`, as with `listallinfo`. */
object QueueParser {

    fun parse(pairs: List<Pair<String, String>>): List<QueueItem> {
        val out = ArrayList<QueueItem>()
        var block: MutableList<Pair<String, String>>? = null

        fun flush() {
            block?.let { b ->
                fun f(key: String): String? = b.firstOrNull { it.first == key }?.second
                val uri = f("file") ?: return@let
                out += QueueItem(
                    id = f("Id")?.toIntOrNull() ?: return@let,
                    pos = f("Pos")?.toIntOrNull() ?: out.size,
                    uri = uri,
                    title = f("Title")?.ifBlank { null } ?: uri.substringAfterLast('/').substringBeforeLast('.'),
                    artist = f("Artist").orEmpty(),
                    album = f("Album").orEmpty(),
                    durationSec = f("Time")?.toIntOrNull() ?: f("duration")?.toDoubleOrNull()?.toInt() ?: 0,
                )
            }
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
}
