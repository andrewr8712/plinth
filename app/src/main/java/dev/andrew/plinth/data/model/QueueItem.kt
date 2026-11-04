package dev.andrew.plinth.data.model

/** One row of MPD's queue. `id` is stable across edits; `pos` is not. */
data class QueueItem(
    val id: Int,
    val pos: Int,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
)
