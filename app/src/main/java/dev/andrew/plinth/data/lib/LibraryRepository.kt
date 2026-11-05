package dev.andrew.plinth.data.lib

import androidx.room.withTransaction
import dev.andrew.plinth.data.model.Album
import dev.andrew.plinth.data.model.AlbumIdentity
import dev.andrew.plinth.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The library cache. `replaceAll` is the only write: MPD's reply is authoritative,
 * so a sync replaces the cache rather than diffing it — and duplicates are collapsed
 * on the way in, which is what turns 198 rows into one album's worth of tracks.
 */
class LibraryRepository(private val db: LibraryDb) {

    private val dao = db.dao()

    suspend fun replaceAll(tracks: List<Track>) {
        val rows = AlbumIdentity.dedupe(tracks)
        db.withTransaction {
            dao.wipeTracks()
            dao.wipeAlbums()
            dao.insertTracks(rows.map { it.toEntity() })
            dao.insertAlbums(
                rows.groupBy { it.albumId }.map { (id, group) -> group.toAlbumEntity(id) },
            )
            // Inside the transaction, so a reader never sees the new library against
            // the old index. Rebuilding is exact even if no sync trigger fired.
            dao.rebuildSearchIndex()
        }
    }

    fun albums(): Flow<List<Album>> = dao.albums().map { rows -> rows.map { it.toAlbum() } }

    fun artists(): Flow<List<String>> = dao.artists()

    fun artistTracks(artist: String): Flow<List<Track>> =
        dao.artistTracks(artist).map { rows -> rows.map { it.toTrack() } }

    fun tracks(): Flow<List<Track>> = dao.tracks().map { rows -> rows.map { it.toTrack() } }

    fun albumTracks(albumId: String): Flow<List<Track>> =
        dao.albumTracks(albumId).map { rows -> rows.map { it.toTrack() } }

    fun folders(): Flow<List<String>> = dao.folders()

    fun folderTracks(folder: String): Flow<List<Track>> =
        dao.folderTracks(folder).map { rows -> rows.map { it.toTrack() } }

    fun search(query: String): Flow<List<Track>> {
        val match = ftsPrefixQuery(query)
        if (match.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.search(match).map { rows -> rows.map { it.toTrack() } }
    }

    /**
     * FTS4 MATCH is a query language of its own: an unmatched quote, a trailing AND
     * or `NEAR/2` is a syntax error, and passing the typed text straight through
     * would also let it mean operators nobody typed. Reducing the text to runs of
     * letters and digits and making each run a prefix keeps every expression
     * well-formed and every hit at the start of a token rather than mid-word.
     * Because the runs are ours, `-`, `*`, quotes and parentheses simply vanish.
     */
    private fun ftsPrefixQuery(raw: String): String {
        val terms = ArrayList<String>(4)
        val term = StringBuilder()
        for (ch in raw) {
            if (ch.isLetterOrDigit()) {
                term.append(ch)
            } else if (term.isNotEmpty()) {
                terms.add(term.toString())
                term.setLength(0)
            }
        }
        if (term.isNotEmpty()) terms.add(term.toString())
        return terms.joinToString(separator = " ") { "$it*" }
    }

    suspend fun trackCount(): Int = dao.trackCount()
}
