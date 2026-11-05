package dev.andrew.plinth.data.lib

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT COUNT(*) FROM track")
    suspend fun trackCount(): Int

    @Query("DELETE FROM track")
    suspend fun wipeTracks()

    @Query("DELETE FROM album")
    suspend fun wipeAlbums()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(rows: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(rows: List<AlbumEntity>)

    @Query("SELECT * FROM album ORDER BY artist COLLATE NOCASE, year, title COLLATE NOCASE")
    fun albums(): Flow<List<AlbumEntity>>

    @Query("SELECT DISTINCT albumArtist FROM track WHERE albumArtist <> '' ORDER BY albumArtist COLLATE NOCASE")
    fun artists(): Flow<List<String>>

    @Query("SELECT * FROM track WHERE albumArtist = :artist ORDER BY album COLLATE NOCASE, disc, trackNo")
    fun artistTracks(artist: String): Flow<List<TrackEntity>>

    /** Every track, for the library's Tracks tab: one list readers can scan alphabetically. */
    @Query("SELECT * FROM track ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, disc, trackNo")
    fun tracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE albumId = :albumId ORDER BY disc, trackNo")
    fun albumTracks(albumId: String): Flow<List<TrackEntity>>

    /** The first path segment, which is how this library's folders are named. */
    @Query(
        "SELECT DISTINCT substr(path, 1, instr(path || '/', '/') - 1) AS folder " +
            "FROM track WHERE path <> '' ORDER BY folder COLLATE NOCASE",
    )
    fun folders(): Flow<List<String>>

    @Query("SELECT * FROM track WHERE path = :folder OR path LIKE :folder || '/%' ORDER BY path, disc, trackNo")
    fun folderTracks(folder: String): Flow<List<TrackEntity>>

    /**
     * Prefix search over the FTS index. [q] is already a MATCH expression built from
     * prefix terms by [LibraryRepository]; raw user text would be an FTS query
     * language of its own and could be a syntax error.
     */
    @Query(
        "SELECT * FROM track WHERE rowid IN " +
            "(SELECT docid FROM track_fts WHERE track_fts MATCH :q) " +
            "ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, disc, trackNo LIMIT 200",
    )
    fun search(q: String): Flow<List<TrackEntity>>

    /**
     * Rebuilds the whole index from the `track` content table. Room's sync triggers
     * already cover row writes, but a rebuild inside the same transaction makes
     * `replaceAll` self-sufficient — including for a database whose FTS table was
     * created by a migration rather than by Room.
     */
    @Query("INSERT INTO track_fts(track_fts) VALUES('rebuild')")
    suspend fun rebuildSearchIndex()
}
