package dev.andrew.plinth.data.lib

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import dev.andrew.plinth.data.model.Album
import dev.andrew.plinth.data.model.AlbumIdentity
import dev.andrew.plinth.data.model.Track

@Entity(tableName = "track")
data class TrackEntity(
    @PrimaryKey val uri: String,
    val albumId: String,
    val disc: Int,
    val trackNo: Int,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val year: Int?,
    val durationSec: Int,
    val channels: Int,
    val sampleRate: Int?,
    val bits: Int?,
    val isDsd: Boolean,
    val path: String,
)

/**
 * The inverted index behind search, over the three things a person types. It is an
 * external-content FTS4 table: `track` stays the source of truth and the index is
 * joined back on `rowid`. Room writes the insert/update/delete sync triggers when it
 * creates this table itself; the migration writes the same ones, and `replaceAll`
 * rebuilds the index regardless so a replaced library can never leave it stale.
 */
@Fts4(contentEntity = TrackEntity::class)
@Entity(tableName = "track_fts")
data class TrackFtsEntity(
    val title: String,
    val artist: String,
    val album: String,
)

@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val trackCount: Int,
    val edition: String?,
    /** Kept so a grid tile can ask for art without loading the album's tracks. */
    val firstUri: String,
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    uri = uri,
    albumId = albumId,
    disc = disc,
    trackNo = trackNo,
    title = title,
    artist = artist,
    albumArtist = albumArtist,
    album = album,
    year = year,
    durationSec = durationSec,
    channels = channels,
    sampleRate = format?.sampleRate,
    bits = format?.bits,
    isDsd = format?.isDsd == true,
    path = path,
)

fun TrackEntity.toTrack(): Track = Track(
    uri = uri,
    albumId = albumId,
    disc = disc,
    trackNo = trackNo,
    title = title,
    artist = artist,
    albumArtist = albumArtist,
    album = album,
    year = year,
    genre = null,
    durationSec = durationSec,
    format = sampleRate?.let { rate ->
        dev.andrew.plinth.data.model.AudioFormat(
            sampleRate = rate,
            bits = bits ?: 16,
            channels = channels,
            isDsd = isDsd,
            dsdRate = if (isDsd) rate / 44_100 else null,
        )
    },
    path = path,
    channels = channels,
)

fun AlbumEntity.toAlbum(): Album = Album(
    id = id,
    title = title,
    artist = artist,
    year = year,
    trackCount = trackCount,
    edition = edition,
    firstUri = firstUri,
)

/** Derives the album row from its tracks: title and year from the first one. */
fun List<Track>.toAlbumEntity(id: String): AlbumEntity {
    val first = minWithOrNull(compareBy({ it.disc }, { it.trackNo })) ?: this.first()
    return AlbumEntity(
        id = id,
        title = first.album,
        artist = first.albumArtist,
        year = mapNotNull { it.year }.minOrNull(),
        trackCount = size,
        edition = AlbumIdentity.edition(first.channels),
        firstUri = first.uri,
    )
}
