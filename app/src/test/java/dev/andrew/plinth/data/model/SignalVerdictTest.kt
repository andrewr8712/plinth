package dev.andrew.plinth.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalVerdictTest {

    private val output = OutputInfo(id = 0, name = "Topping DX5 II", enabled = true, dopAttribute = true)

    private fun track(ext: String, rate: Int, bits: Int, channels: Int): Track {
        val format = if (ext == "dsf") {
            AudioFormat(sampleRate = rate, bits = 1, channels = channels, isDsd = true, dsdRate = rate / 44_100)
        } else {
            AudioFormat(sampleRate = rate, bits = bits, channels = channels, isDsd = false, dsdRate = null)
        }
        return Track(
            uri = "Album/track.$ext", albumId = "a1", disc = 1, trackNo = 1, title = "T",
            artist = "A", albumArtist = "A", album = "Al", year = 2000, genre = null,
            durationSec = 200, format = format, path = "Album", channels = channels,
        )
    }

    private fun status(audio: String?, gain: String?) = PlayerState(
        state = PlaybackState.Play, songId = 1, songPos = 0, elapsed = 10.0, duration = 200.0,
        queueVersion = 1, random = false, repeat = false, single = false, consume = false,
        format = AudioFormat.parse(audio), bitrateKbps = null, audioOutRateHz = null,
        replayGainMode = gain,
    )

    @Test
    fun `matching pcm with rg off is bit perfect`() {
        assertEquals(
            SignalVerdict.BitPerfect,
            SignalVerdicts.of(status("96000:24:2", "off"), track("flac", 96000, 24, 2), output),
        )
    }

    @Test
    fun `rate mismatch is transcoded`() {
        val verdict = SignalVerdicts.of(status("96000:24:2", "off"), track("flac", 192000, 24, 2), output)
        assertTrue("expected Transcoded, got $verdict", verdict is SignalVerdict.Transcoded)
    }

    @Test
    fun `dsd over dop is bit perfect and labelled`() {
        val t = track("dsf", 2_822_400, 1, 2)
        assertEquals(SignalVerdict.BitPerfect, SignalVerdicts.of(status("dsd64:2", "off"), t, output))
        assertEquals("DSD64 · DoP", SignalVerdicts.labelFor(t.format, t.uri))
    }

    @Test
    fun `six channel file is multichannel, not transcoded`() {
        val verdict = SignalVerdicts.of(status("96000:24:6", "off"), track("flac", 96000, 24, 6), output)
        assertTrue("expected Multichannel, got $verdict", verdict is SignalVerdict.Multichannel)
    }

    @Test
    fun `missing format is unknown, not a crash`() {
        val noFormat = track("flac", 96000, 24, 2).copy(format = null)
        assertEquals(SignalVerdict.Unknown, SignalVerdicts.of(status(null, "off"), noFormat, output))
        assertEquals(SignalVerdict.Unknown, SignalVerdicts.of(status("96000:24:2", "off"), null, output))
    }

    @Test
    fun `replay gain on downgrades the verdict`() {
        val verdict = SignalVerdicts.of(status("44100:16:2", "track"), track("flac", 44100, 16, 2), output)
        assertTrue("expected Transcoded, got $verdict", verdict is SignalVerdict.Transcoded)
    }

    @Test
    fun `the link cell names the path, and prints a rate only when it differs`() {
        assertEquals(
            "USB · DX5 II",
            SignalVerdicts.stripCells(status("96000:24:2", "off"), track("flac", 96000, 24, 2), output).link,
        )
        assertEquals(
            "44.1 kHz → 48 kHz",
            SignalVerdicts.stripCells(status("48000:24:2", "off"), track("flac", 44100, 16, 2), output).link,
        )
    }

    @Test
    fun `the three cells are format, link, verdict — never the rate twice`() {
        val cells = SignalVerdicts.stripCells(status("96000:24:2", "off"), track("flac", 96000, 24, 2), output)
        assertEquals(listOf("FLAC · 24/96", "USB · DX5 II", "Bit-perfect"), listOf(cells.source, cells.link, cells.verdict))
    }

    @Test
    fun `lossy extensions are named as lossy`() {
        val mp3 = track("mp3", 44100, 16, 2)
        assertEquals(SignalVerdict.Lossy, SignalVerdicts.of(status("44100:16:2", "off"), mp3, output))
    }
}
