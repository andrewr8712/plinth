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

    @Query("SELECT DISTINCT albumArtist FROM track ORDER BY albumArtist COLLATE NOCASE")
    fun artists(): Flow<List<String>>

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

    /** Prefix match on the three things a person types. LIKE is ASCII-case-insensitive. */
    @Query(
        "SELECT * FROM track WHERE title LIKE :q || '%' OR album LIKE :q || '%' OR artist LIKE :q || '%' " +
            "ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, disc, trackNo LIMIT 200",
    )
    fun search(q: String): Flow<List<TrackEntity>>
}
