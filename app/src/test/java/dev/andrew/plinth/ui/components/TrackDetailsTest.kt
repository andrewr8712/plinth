package dev.andrew.plinth.ui.components

import dev.andrew.plinth.data.model.AudioFormat
import dev.andrew.plinth.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The details sheet's rows, built without Android so the field rules stay pinned. */
class TrackDetailsTest {

    private fun track(
        uri: String = "Drones/03 - The Dark Forest.flac",
        disc: Int = 1,
        trackNo: Int = 3,
        title: String = "The Dark Forest",
        artist: String = "Muse",
        albumArtist: String = "Muse",
        album: String = "Drones",
        year: Int? = 2015,
        genre: String? = "Rock",
        durationSec: Int = 271,
        format: AudioFormat? = AudioFormat(
            sampleRate = 96_000,
            bits = 24,
            channels = 2,
            isDsd = false,
            dsdRate = null,
        ),
        path: String = "Drones",
        channels: Int = 2,
    ) = Track(
        uri = uri,
        albumId = "a1",
        disc = disc,
        trackNo = trackNo,
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        year = year,
        genre = genre,
        durationSec = durationSec,
        format = format,
        path = path,
        channels = channels,
    )

    @Test
    fun theFileSectionNamesTheFormatAndTheLength() {
        val rows = fileDetailRows(track(), tags = null)

        assertEquals(listOf("Name", "Path", "Format", "Length"), rows.map { it.label })
        assertEquals("03 - The Dark Forest.flac", rows[0].value)
        assertEquals("Drones", rows[1].value)
        assertEquals("FLAC · 24/96 · 2 ch", rows[2].value)
        assertEquals("4:31", rows[3].value)
    }

    @Test
    fun aDsdFileNamesItsRateNotBits() {
        val dsd = track(
            uri = "Raven/01 - Luminol.dsf",
            format = AudioFormat(2_822_400, 1, 2, isDsd = true, dsdRate = 64),
            channels = 2,
        )

        assertEquals("DSD64 · 2 ch", formatDetail(dsd))
    }

    @Test
    fun lastModifiedOnlyAppearsWhenTheServerReportedIt() {
        val raw = listOf("Last-Modified" to "2026-06-03T12:28:14Z")

        val withTag = fileDetailRows(track(), raw)
        assertEquals("2026-06-03T12:28:14Z", withTag.first { it.label == "Modified" }.value)

        val without = fileDetailRows(track(), null)
        assertFalse(without.any { it.label == "Modified" })
    }

    @Test
    fun tagsLeadWithTheFieldsEveryoneRecognisesAndJoinDuplicates() {
        val raw = listOf(
            "file" to "Drones/03 - The Dark Forest.flac",
            "Last-Modified" to "2026-06-03T12:28:14Z",
            "Format" to "96000:24:2",
            "Time" to "271",
            "duration" to "271.116",
            "Track" to "3",
            "AlbumArtist" to "Muse",
            "Artist" to "Muse",
            "Artist" to "Matthew Bellamy",
            "Album" to "Drones",
            "Title" to "The Dark Forest",
            "Date" to "2015",
            "Composer" to "Muse",
        )

        val rows = tagDetailRows(raw)

        assertEquals(
            listOf("Title", "Artist", "Album artist", "Album", "Date", "Track", "Composer"),
            rows.map { it.label },
        )
        assertEquals("Muse · Matthew Bellamy", rows.first { it.label == "Artist" }.value)
        // The file facts already have their own section; they are not tags as well.
        val labels = rows.map { it.label }
        assertFalse(labels.any { it in setOf("file", "Last-Modified", "Format", "Time", "duration") })
    }

    @Test
    fun aFileWithNoTagsProducesNoTagRows() {
        val raw = listOf(
            "file" to "AKIRA/01 - KANEDA.dff",
            "Last-Modified" to "2026-05-27T19:35:11Z",
            "Time" to "191",
            "duration" to "190.676",
        )

        assertTrue(tagDetailRows(raw).isEmpty())
    }

    @Test
    fun cachedTagsSkipTheFallbackNoise() {
        val rows = cachedTagRows(track(trackNo = 0, albumArtist = "Muse"))

        assertEquals(listOf("Title", "Artist", "Album", "Date", "Genre"), rows.map { it.label })
    }

    @Test
    fun cachedTagsKeepADifferentAlbumArtist() {
        val rows = cachedTagRows(track(albumArtist = "Various Artists"))

        assertEquals("Various Artists", rows.first { it.label == "Album artist" }.value)
    }
}
