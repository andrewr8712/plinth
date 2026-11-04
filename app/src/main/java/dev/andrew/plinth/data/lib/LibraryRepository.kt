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
        }
    }

    fun albums(): Flow<List<Album>> = dao.albums().map { rows -> rows.map { it.toAlbum() } }

    fun artists(): Flow<List<String>> = dao.artists()

    fun albumTracks(albumId: String): Flow<List<Track>> =
        dao.albumTracks(albumId).map { rows -> rows.map { it.toTrack() } }

    fun folders(): Flow<List<String>> = dao.folders()

    fun folderTracks(folder: String): Flow<List<Track>> =
        dao.folderTracks(folder).map { rows -> rows.map { it.toTrack() } }

    fun search(query: String): Flow<List<Track>> {
        val q = query.trim()
        if (q.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.search(q).map { rows -> rows.map { it.toTrack() } }
    }

    suspend fun trackCount(): Int = dao.trackCount()
}
