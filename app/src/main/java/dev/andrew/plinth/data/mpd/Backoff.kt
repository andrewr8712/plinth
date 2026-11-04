package dev.andrew.plinth.data.mpd

/**
 * Doubles the wait between attempts and stops growing at 30 s, so a streamer that
 * is off overnight is picked up within half a minute of coming back.
 */
class Backoff(
    private val baseMs: Long = 1_000,
    private val capMs: Long = 30_000,
) {
    fun next(attempt: Int): Long {
        if (attempt <= 0) return 0
        val shifted = baseMs shl (attempt - 1).coerceAtMost(5)
        return shifted.coerceAtMost(capMs)
    }
}
