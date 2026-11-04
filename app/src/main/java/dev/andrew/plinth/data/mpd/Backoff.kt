package dev.andrew.plinth.data.mpd

/**
 * Doubles the wait between attempts and stops growing at 30 s, so a streamer that
 * is off overnight is picked up within half a minute of coming back.
 */
class Backoff(
    private val baseMs: Long = 1_000,
    private val capMs: Long = 30_000,
    private val jitter: () -> Double = { Math.random() },
) {
    fun next(attempt: Int): Long {
        if (attempt <= 0) return 0
        val shifted = baseMs shl (attempt - 1).coerceAtMost(5)
        val capped = shifted.coerceAtMost(capMs)
        // ±20 %: a dead network must not bring every client back on the same tick.
        val spread = capped * 0.2 * (jitter() * 2 - 1)
        return (capped + spread).toLong().coerceAtLeast(1)
    }
}
