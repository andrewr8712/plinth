package dev.andrew.plinth.ui.vm

import dev.andrew.plinth.data.mpd.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The chip's copy has to be true to the failure, not a generic "error". */
class OfflineCopyTest {

    @Test
    fun `a timeout reads as the streamer not answering`() {
        assertEquals(
            "Streamer not answering",
            offlineCopy(ConnectionState.Unavailable("MPD did not answer in time")),
        )
    }

    @Test
    fun `a refused socket reads as a wrong address`() {
        assertEquals(
            "Streamer refused the connection",
            offlineCopy(ConnectionState.Unavailable("Connection refused")),
        )
    }

    @Test
    fun `a missing route reads as unreachable`() {
        assertEquals(
            "Streamer unreachable",
            offlineCopy(ConnectionState.Unavailable("No route to host")),
        )
    }

    @Test
    fun `connecting and up have nothing to report`() {
        assertNull(offlineCopy(ConnectionState.Connecting(failuresAgo = 1)))
        assertNull(offlineCopy(ConnectionState.Connecting(failuresAgo = 0)))
        assertNull(offlineCopy(ConnectionState.Up))
    }
}
