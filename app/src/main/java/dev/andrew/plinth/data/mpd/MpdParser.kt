package dev.andrew.plinth.data.mpd

/**
 * Parses MPD's line replies. Values may contain `": "` (times, URIs), so a line
 * splits on its **first** separator only; `OK` is a terminator, never a field.
 *
 * No regex here on purpose: Android's ICU engine is stricter than the JVM's about
 * bare braces, and an `ACK` is straightforward to take apart by hand.
 */
object MpdParser {

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

    /** `ACK [5@0] {setvol} No mixer` → code 5, command `setvol`, message `No mixer`. */
    private fun ackOf(text: String): MpdReply.Ack? {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (!first.startsWith(PREFIX)) return null

        val closeBracket = first.indexOf(']', PREFIX.length)
        if (closeBracket < 0) return null
        val codeAndIndex = first.substring(PREFIX.length, closeBracket).split('@')
        val code = codeAndIndex.getOrNull(0)?.toIntOrNull() ?: return null
        val index = codeAndIndex.getOrNull(1)?.toIntOrNull() ?: 0

        val openBrace = first.indexOf('{', closeBracket)
        val closeBrace = if (openBrace < 0) -1 else first.indexOf('}', openBrace + 1)
        if (openBrace < 0 || closeBrace < 0) {
            return MpdReply.Ack(code, index, "", first.substring(closeBracket + 1).trim())
        }
        return MpdReply.Ack(
            code = code,
            commandIndex = index,
            command = first.substring(openBrace + 1, closeBrace),
            message = first.substring(closeBrace + 1).trim(),
        )
    }

    private const val PREFIX = "ACK ["
}
