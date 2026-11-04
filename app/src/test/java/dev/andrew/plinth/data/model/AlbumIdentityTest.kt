package dev.andrew.plinth.data.model

import dev.andrew.plinth.data.mpd.LibraryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumIdentityTest {

    private fun fixture(name: String): List<Track> {
        val text = javaClass.getResource("/$name")!!.readText()
        return LibraryParser.parse(pairsOf(text))
    }

    private fun pairsOf(text: String): List<Pair<String, String>> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cut = line.indexOf(": ")
                if (cut <= 0) null else line.substring(0, cut) to line.substring(cut + 2)
            }
            .toList()

    @Test
    fun `duplicate discographies collapse to one album's worth of tracks`() {
        val tracks = fixture("listallinfo_manson_dupes.txt")
        assertEquals(198, tracks.size)
        val deduped = AlbumIdentity.dedupe(tracks)
        assertEquals(99, deduped.size)
        assertEquals(1, deduped.map { it.albumId }.distinct().size)
    }

    @Test
    fun `box sets in several folders merge`() {
        val t = fixture("listallinfo_mellon_collie.txt")
        val folders = t.map { it.path }.distinct()
        assertTrue("expected several folders, got $folders", folders.size >= 4)
        assertEquals(1, t.map { it.albumId }.distinct().size)
    }

    @Test
    fun `multichannel mixes get their own edition`() {
        val t = fixture("listallinfo_raven.txt")
        val stereo = t.first { it.channels <= 2 }
        assertTrue(t.filter { it.channels > 2 }.all { it.albumId != stereo.albumId })
        assertEquals("5.1", AlbumIdentity.edition(6))
        assertEquals("4.0", AlbumIdentity.edition(4))
        assertNull(AlbumIdentity.edition(2))
    }

    @Test
    fun `normalise lowers, trims and collapses`() {
        assertEquals("kind of blue", AlbumIdentity.normalise("  Kind  of   Blue  "))
        assertEquals("kind of blue", AlbumIdentity.normalise("Kind Of Blue *"))
    }

    @Test
    fun `same album in different case is the same album`() {
        val a = AlbumIdentity.key("Steven Wilson", "Steven Wilson", "The Raven That Refused to Sing (and other stories)", 2)
        val b = AlbumIdentity.key("steven  wilson", "someone", "the raven that refused to sing (and other stories)", 2)
        assertEquals(a, b)
    }

    @Test
    fun `duplicate rows keep the first occurrence`() {
        val t = fixture("listallinfo_manson_dupes.txt")
        val deduped = AlbumIdentity.dedupe(t)
        assertEquals(t.first().uri, deduped.first().uri)
        assertEquals(deduped.size, deduped.map { "${it.disc}|${it.trackNo}|${it.title}" }.distinct().size)
    }
}
