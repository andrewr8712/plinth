package dev.andrew.plinth.data.loudness

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stale rule, before it is allowed to replace a meter reading with a warning. */
class LoudnessStalenessTest {

    @Test
    fun `an envelope older than the library is stale`() {
        assertTrue(isLoudnessStale(envelopeDbUpdate = 100, libraryDbUpdate = 200))
    }

    @Test
    fun `an envelope from the same or a newer database is not`() {
        assertFalse(isLoudnessStale(envelopeDbUpdate = 200, libraryDbUpdate = 200))
        assertFalse(isLoudnessStale(envelopeDbUpdate = 300, libraryDbUpdate = 200))
    }

    @Test
    fun `an envelope with no recorded timestamp cannot be called stale`() {
        assertFalse(isLoudnessStale(envelopeDbUpdate = 0, libraryDbUpdate = 200))
    }

    @Test
    fun `a streamer that did not report db_update is not evidence of staleness`() {
        assertFalse(isLoudnessStale(envelopeDbUpdate = 100, libraryDbUpdate = null))
    }
}
