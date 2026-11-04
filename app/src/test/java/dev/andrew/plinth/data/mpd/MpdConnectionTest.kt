package dev.andrew.plinth.data.mpd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MpdConnectionTest {

    @Test
    fun `failure climbs from options to unreachable`() = runBlocking {
        val seen = mutableListOf<ConnectionState>()
        var naps = 0
        val conn = MpdConnection(
            host = "127.0.0.1",
            port = 1,
            socketFactory = { throw IOException("no route to host") },
            sleep = {
                naps++
                if (naps >= 3) throw CancellationException("test done")
            },
        )
        val job = launch { conn.run() }
        job.join()
        seen += conn.state.value
        assertTrue("expected Unavailable, saw $seen", seen.last() is ConnectionState.Unavailable)
    }

    @Test
    fun `backoff doubles and caps at thirty seconds`() {
        val b = Backoff()
        assertEquals(1_000L, b.next(1))
        assertEquals(2_000L, b.next(2))
        assertEquals(4_000L, b.next(3))
        assertEquals(30_000L, b.next(20))
    }
}
