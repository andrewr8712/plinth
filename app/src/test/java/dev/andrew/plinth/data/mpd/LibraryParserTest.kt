package dev.andrew.plinth.data.mpd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryParserTest {

    private fun pairsOf(text: String): List<Pair<String, String>> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cut = line.indexOf(": ")
                if (cut <= 0) null else line.substring(0, cut) to line.substring(cut + 2)
            }
            .toList()

    @Test
    fun `sacd rows without Format or AlbumArtist do not throw`() {
        val t = LibraryParser.parse(pairsOf("file: A/01.dsf\nTitle: X\nArtist: Y\nTime: 200\nOK\n"))
        assertEquals(1, t.size)
        // no Format tag, but the extension still says DSD — the plan's null assertion was wrong
        assertTrue(t[0].format!!.isDsd)
        assertEquals("Y", t[0].albumArtist)
        assertEquals(200, t[0].durationSec)
    }

    @Test
    fun `dsd format derives from extension when tags are missing`() {
        val t = LibraryParser.parse(pairsOf("file: A/01.dsf\nTitle: X\nTime: 200\nOK\n"))
        assertTrue(t[0].format!!.isDsd)
        assertEquals(2, t[0].channels)
    }

    @Test
    fun `blocks start at file and carry their tags`() {
        val text = javaClass.getResource("/listallinfo_sacd.txt")!!.readText()
        val t = LibraryParser.parse(pairsOf(text))
        assertEquals(5, t.size)
        assertEquals("Pink Floyd", t[0].artist)
        assertEquals("Wish You Were Here", t[0].album)
        assertEquals(2011, t[0].year) // the SACD reissue date in the fixture
        assertTrue("SACD rows infer DSD from .dsf", t.all { it.format!!.isDsd })
        assertTrue(t.all { it.channels == 2 })
        assertEquals(1, t.map { it.albumId }.distinct().size)
    }

    @Test
    fun `stereo and surround rows of one album get different ids`() {
        val text = javaClass.getResource("/listallinfo_raven.txt")!!.readText()
        val t = LibraryParser.parse(pairsOf(text))
        assertEquals(18, t.size)
        assertEquals(2, t.map { it.albumId }.distinct().size)
    }

    @Test
    fun `title falls back to the file name when the tag is missing`() {
        val t = LibraryParser.parse(pairsOf("file: Dir/07 - Track.flac\nTime: 100\nOK\n"))
        assertEquals("07 - Track", t[0].title)
    }
}
