package dev.andrew.plinth.data.mpd

/**
 * Parses MPD's line replies. Values may contain `": "` (times, URIs), so a line
 * splits on its **first** separator only; `OK` is a terminator, never a field.
 */
object MpdParser {

    private val ACK = Regex("""^ACK \[(\d+)@(\d+)] \{([^}]*)} (.*)$""")

    fun parse(text: String): MpdReply = ackOf(text) ?: MpdReply.Ok(pairs(text))

    fun pairs(text: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (line in text.lineSequence()) {
            if (line.isBlank() || line == "OK") continue
            val cut = line.indexOf(": ")
            if (cut <= 0) continue
            out += line.substring(0, cut) to line.substring(cut + 2)
        }
        return out
    }

    private fun ackOf(text: String): MpdReply.Ack? {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val m = ACK.find(first.trim()) ?: return null
        return MpdReply.Ack(
            code = m.groupValues[1].toInt(),
            commandIndex = m.groupValues[2].toInt(),
            command = m.groupValues[3],
            message = m.groupValues[4].trim(),
        )
    }
}
