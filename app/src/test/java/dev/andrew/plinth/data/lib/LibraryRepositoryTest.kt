package dev.andrew.plinth.data.lib

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.data.mpd.LibraryParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {

    private lateinit var db: LibraryDb
    private lateinit var repo: LibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDb::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LibraryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun fixture(name: String): List<Track> {
        val text = javaClass.getResource("/$name")!!.readText()
        val pairs = text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cut = line.indexOf(": ")
                if (cut <= 0) null else line.substring(0, cut) to line.substring(cut + 2)
            }
            .toList()
        return LibraryParser.parse(pairs)
    }

    private fun track(
        uri: String,
        title: String,
        artist: String,
        album: String,
        albumId: String = uri,
    ): Track = Track(
        uri = uri, albumId = albumId, disc = 1, trackNo = 1, title = title,
        artist = artist, albumArtist = artist, album = album, year = null,
        genre = null, durationSec = 200, format = null, path = album, channels = 2,
    )

    private fun massiveAttack(): List<Track> = listOf(
        Track(
            uri = "Mezzanine/01 - Angel.flac", albumId = "a1", disc = 1, trackNo = 1,
            title = "Angel", artist = "Massive Attack", albumArtist = "Massive Attack",
            album = "Mezzanine", year = 1998, genre = "Trip Hop", durationSec = 378,
            format = null, path = "Mezzanine", channels = 2,
        ),
        Track(
            uri = "Mezzanine/02 - Teardrop.flac", albumId = "a1", disc = 1, trackNo = 2,
            title = "Teardrop", artist = "Massive Attack", albumArtist = "Massive Attack",
            album = "Mezzanine", year = 1998, genre = "Trip Hop", durationSec = 331,
            format = null, path = "Mezzanine", channels = 2,
        ),
        Track(
            uri = "Dummy/01 - Teardrop (Live).flac", albumId = "a2", disc = 1, trackNo = 1,
            title = "Teardrop (Live)", artist = "Someone Else", albumArtist = "Someone Else",
            album = "Dummy", year = 1994, genre = null, durationSec = 300,
            format = null, path = "Dummy", channels = 2,
        ),
    )

    @Test
    fun `replaceAll is idempotent and keeps one album row`() = runBlocking {
        val manson = fixture("listallinfo_manson_dupes.txt")
        assertEquals(198, manson.size)
        repo.replaceAll(manson)
        repo.replaceAll(manson)
        assertEquals(99, repo.trackCount())
        assertEquals(1, repo.albums().first().size)
    }

    @Test
    fun `search is prefix and case insensitive`() = runBlocking {
        repo.replaceAll(massiveAttack())
        // the fixture holds "Teardrop" and "Teardrop (Live)": prefix matches both
        assertEquals(2, repo.search("teard").first().size)
        assertEquals(2, repo.search("TEARD").first().size)
        // but not the middle of a word
        assertTrue("search must be a prefix match", repo.search("rop").first().isEmpty())
        assertTrue("search must be a prefix match", repo.search("eardrop").first().isEmpty())
    }

    @Test
    fun `search matches artist and album prefixes`() = runBlocking {
        repo.replaceAll(massiveAttack())
        // "massive" is only in the artist, "mezzanine" only in the album
        assertEquals(2, repo.search("massive").first().size)
        assertEquals(2, repo.search("mezzanine").first().size)
        // every term is a prefix, so two typed words still hit
        assertEquals(2, repo.search("mass att").first().size)
    }

    @Test
    fun `search never passes FTS syntax through`() = runBlocking {
        repo.replaceAll(massiveAttack())

        // These read as FTS4 syntax, not text: raw, a lone quote or a trailing AND
        // is an SQL error and `*`/`OR` would widen the search. Sanitised they are
        // just the same prefix query.
        assertEquals(2, repo.search("\"teard").first().size)
        assertEquals(2, repo.search("teard\"*").first().size)
        assertEquals(2, repo.search("(teard)").first().size)
        assertEquals(2, repo.search("-teard").first().size)
        assertEquals(2, repo.search("teard*").first().size)

        // With nothing left to look for the result is empty, never "everything".
        val noise = listOf("\"", "*", "-", "(", ")", "()", "\"*\"", "%%", "AND", "OR", "NOT", "foo AND", "NEAR/2")
        for (query in noise) {
            assertTrue("'$query' must not match the library", repo.search(query).first().isEmpty())
        }
    }

    @Test
    fun `a blank or punctuation-only search returns nothing`() = runBlocking {
        repo.replaceAll(massiveAttack())
        assertTrue(repo.search("").first().isEmpty())
        assertTrue(repo.search("   ").first().isEmpty())
    }

    @Test
    fun `search accepts CJK text`() = runBlocking {
        repo.replaceAll(
            listOf(track(uri = "Akira/01.flac", title = "残酷な天使のテーゼ", artist = "高橋洋子", album = "残酷な天使のテーゼ")),
        )
        // No SQL error, and the start of the CJK run is a prefix like any other token.
        assertEquals(1, repo.search("残酷").first().size)
        assertEquals(0, repo.search("天使").first().size)
    }

    @Test
    fun `the search index follows replaceAll`() = runBlocking {
        repo.replaceAll(listOf(track("Old/01.flac", "Old Song", "Old Artist", "Old Album", albumId = "old")))
        assertEquals(1, repo.search("old").first().size)

        // A second sync replaces the library wholesale: the index has to follow the
        // deletes as well as the inserts.
        repo.replaceAll(
            listOf(
                track("New/01.flac", "New Song", "New Artist", "New Album", albumId = "new"),
                track("New/02.flac", "Another Song", "New Artist", "New Album", albumId = "new"),
            ),
        )
        assertEquals(2, repo.search("new").first().size)
        assertTrue("replaced rows must leave the index", repo.search("old").first().isEmpty())

        repo.replaceAll(emptyList())
        assertTrue("an empty library has nothing to find", repo.search("new").first().isEmpty())
    }

    @Test
    fun `folders returns the first path segment only`() = runBlocking {
        repo.replaceAll(massiveAttack())
        assertEquals(listOf("Dummy", "Mezzanine"), repo.folders().first())
    }

    @Test
    fun `album tracks come back in disc then track order`() = runBlocking {
        repo.replaceAll(massiveAttack())
        val tracks = repo.albumTracks("a1").first()
        assertEquals(listOf("Angel", "Teardrop"), tracks.map { it.title })
    }

    @Test
    fun `artists are distinct and sorted`() = runBlocking {
        repo.replaceAll(massiveAttack())
        assertEquals(listOf("Massive Attack", "Someone Else"), repo.artists().first())
    }

    @Test
    fun `an album carries the uri its art is asked for`() = runBlocking {
        repo.replaceAll(fixture("listallinfo_raven.txt"))
        val albums = repo.albums().first()
        val uris = repo.tracks().first().map { it.uri }.toSet()
        assertTrue(albums.all { it.firstUri in uris })
        // track 1 of the album, not an arbitrary row
        val surround = albums.first { it.edition == "5.1" }
        val firstOfIt = repo.albumTracks(surround.id).first().first()
        assertEquals(firstOfIt.uri, surround.firstUri)
    }

    @Test
    fun `the tracks tab lists everything, artist by artist`() = runBlocking {
        repo.replaceAll(massiveAttack())
        val all = repo.tracks().first()
        assertEquals(3, all.size)
        assertEquals(listOf("Angel", "Teardrop", "Teardrop (Live)"), all.map { it.title })
    }

    @Test
    fun `a blank album artist is not listed as an artist`() = runBlocking {
        repo.replaceAll(fixture("listallinfo_untagged.txt"))
        assertTrue("untagged rips have no artist to list", repo.artists().first().isEmpty())
    }

    @Test
    fun `artist tracks are that artist's, in album order`() = runBlocking {
        repo.replaceAll(massiveAttack())
        val tracks = repo.artistTracks("Massive Attack").first()
        assertEquals(listOf("Angel", "Teardrop"), tracks.map { it.title })
        assertTrue(repo.artistTracks("Someone Else").first().none { it.artist == "Massive Attack" })
    }

    @Test
    fun `album row carries the edition and a track count`() = runBlocking {
        repo.replaceAll(fixture("listallinfo_raven.txt"))
        val albums = repo.albums().first()
        assertEquals(2, albums.size)
        val surround = albums.first { it.edition == "5.1" }
        assertEquals(6, surround.trackCount)
        assertTrue(albums.any { it.edition == null })
    }

    @Test
    fun `search matches album and artist as well as title`() = runBlocking {
        repo.replaceAll(massiveAttack())
        assertEquals(2, repo.search("mezzanine").first().size)
        assertEquals(3, repo.search("massive").first().size + repo.search("someone").first().size)
    }

    /** The live bug: three untagged records opened as one 33-track album with a blank header. */
    @Test
    fun `untagged folders stay separate all the way into the album table`() = runBlocking {
        repo.replaceAll(fixture("listallinfo_untagged.txt"))
        assertEquals(33, repo.trackCount())

        val albums = repo.albums().first()
        assertEquals(3, albums.size)
        assertTrue("album titles must never be blank: ${albums.map { it.title }}", albums.all { it.title.isNotBlank() })
        assertEquals(33, albums.sumOf { it.trackCount })

        val akira = albums.first { it.title.contains("AKIRA") }
        assertEquals(10, akira.trackCount)
        val tracks = repo.albumTracks(akira.id).first()
        assertEquals(10, tracks.size)
        assertEquals(1, tracks.map { it.albumId }.toSet().size)
        assertTrue(
            "album detail must not leak another record's rows",
            tracks.all { it.path == "Geinoh Yamashirogumi  Symphonic Suite AKIRA (2016)" },
        )
    }
}
