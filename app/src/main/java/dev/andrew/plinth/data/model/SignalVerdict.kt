package dev.andrew.plinth.data.model

/** What the chain is doing to the file, as far as MPD will admit. */
sealed interface SignalVerdict {
    data object BitPerfect : SignalVerdict
    data object Lossy : SignalVerdict
    data class Transcoded(val from: String, val to: String) : SignalVerdict
    data class Multichannel(val channels: Int) : SignalVerdict
    data object Unknown : SignalVerdict
}

/** The three cells under the artist line: what went in, the path, the verdict. */
data class StripCells(val source: String, val link: String, val verdict: String)

object SignalVerdicts {

    fun of(state: PlayerState, track: Track?, output: OutputInfo?): SignalVerdict {
        val file = track?.format
        val stream = state.format
        if (track == null || file == null || stream == null) return SignalVerdict.Unknown

        // multi-channel is a fact about the file, not a fault in the chain
        if (stream.channels > 2) return SignalVerdict.Multichannel(stream.channels)
        if (isLossy(track.uri)) return SignalVerdict.Lossy

        // any processing at all is no longer bit-perfect
        val gain = state.replayGainMode
        if (gain != null && gain != "off" && gain != "0") {
            return SignalVerdict.Transcoded(rateLabel(file), rateLabel(stream))
        }
        if (file.sampleRate != stream.sampleRate || (!file.isDsd && file.bits != stream.bits)) {
            return SignalVerdict.Transcoded(rateLabel(file), rateLabel(stream))
        }
        return SignalVerdict.BitPerfect
    }

    fun labelFor(
        format: AudioFormat?,
        uri: String = "",
        bitrateKbps: Int? = null,
        dop: Boolean = false,
    ): String {
        if (format == null) return "—"
        val ext = uri.substringAfterLast('.', "").uppercase()
        if (format.isDsd) return "DSD${format.dsdRate}" + if (dop) " · DoP" else ""
        val kind = when (ext) {
            "MP3" -> "MP3"
            "AAC", "M4A" -> "AAC"
            "OGG", "OPUS" -> ext
            "FLAC" -> "FLAC"
            "WAV" -> "WAV"
            "AIFF", "AIF" -> "AIFF"
            else -> ext.ifBlank { "PCM" }
        }
        val rate = "${format.bits}/${format.sampleRate / 1000}"
        return if (bitrateKbps != null && kind in LOSSY_KINDS) "$kind · $bitrateKbps kbps"
        else "$kind · $rate"
    }

    /**
     * Source / Link / Verdict. The conversion rate appears in the Link cell only when
     * there is a conversion — otherwise that cell names the path, and no fact is printed twice.
     */
    fun stripCells(state: PlayerState, track: Track?, output: OutputInfo?): StripCells {
        val verdict = of(state, track, output)
        val source = labelFor(
            track?.format,
            track?.uri ?: "",
            state.bitrateKbps,
            dop = output?.dopAttribute == true,
        )

        val file = track?.format
        val stream = state.format
        val link = if (file != null && stream != null &&
            (file.sampleRate != stream.sampleRate || (!file.isDsd && file.bits != stream.bits))
        ) {
            "${kHz(file.sampleRate)} → ${kHz(stream.sampleRate)}"
        } else {
            "USB · ${shortName(output?.name)}"
        }

        return StripCells(source, link, verdictLabel(verdict))
    }

    private fun verdictLabel(verdict: SignalVerdict): String = when (verdict) {
        is SignalVerdict.BitPerfect -> "Bit-perfect"
        is SignalVerdict.Lossy -> "Lossy"
        is SignalVerdict.Transcoded -> "Transcoded"
        is SignalVerdict.Multichannel -> if (verdict.channels == 6) "5.1" else "${verdict.channels}ch"
        is SignalVerdict.Unknown -> "—"
    }

    private fun rateLabel(format: AudioFormat): String =
        if (format.isDsd) "DSD${format.dsdRate}" else kHz(format.sampleRate) + "/" + format.bits

    private fun kHz(hz: Int): String {
        val k = hz / 1000.0
        return if (k % 1.0 == 0.0) "${k.toInt()} kHz" else "%.1f kHz".format(k)
    }

    private fun shortName(name: String?): String {
        val clean = name?.trim().orEmpty()
        if (clean.isEmpty()) return "output"
        val tokens = clean.split(' ')
        return if (tokens.size >= 3) tokens.drop(1).joinToString(" ") else clean
    }

    /** Lossy formats are identified by extension: MPD reports the decoded format only. */
    private fun isLossy(uri: String): Boolean = uri.substringAfterLast('.', "").lowercase() in LOSSY_EXT

    private val LOSSY_EXT = setOf("mp3", "aac", "m4a", "ogg", "opus", "wma")
    private val LOSSY_KINDS = setOf("MP3", "AAC", "OGG", "OPUS")
}
