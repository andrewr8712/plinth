package dev.andrew.plinth.data.model

/** One song, as MPD's database describes it. */
data class Track(
    val uri: String,
    val albumId: String,
    val disc: Int,
    val trackNo: Int,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val year: Int?,
    val genre: String?,
    val durationSec: Int,
    val format: AudioFormat?,
    val path: String,
    val channels: Int,
)

/** One configured MPD output. This streamer has exactly one. */
data class OutputInfo(
    val id: Int,
    val name: String,
    val enabled: Boolean,
    val dopAttribute: Boolean,
)
