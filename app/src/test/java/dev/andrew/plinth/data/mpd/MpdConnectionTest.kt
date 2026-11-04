package dev.andrew.plinth.data.mpd

import dev.andrew.plinth.data.model.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class MpdConnectionTest {

    private lateinit var server: FakeMpdServer

    @Before
    fun setUp() {
        server = FakeMpdServer().start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `failure climbs from options to unreachable`() = runBlocking {
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
        assertTrue("expected Unavailable, saw ${conn.state.value}", conn.state.value is ConnectionState.Unavailable)
    }

    @Test(timeout = 20_000)
    fun `commands still work while the idle connection is parked`() {
        // idle never answers: that is what a parked idle connection looks like
        server.onCommand("idle") { null }
        server.onCommand("status") { "state: stop\nplaylist: 4\nOK\n" }

        val conn = MpdConnection(host = "127.0.0.1", port = server.port)
        runBlocking {
            // cancelled inside the block: runBlocking joins children before it returns
            val job = launch(Dispatchers.IO) { conn.run() }
            try {
                withTimeout(5_000) { conn.state.first { it is ConnectionState.Up } }
                val st = withTimeout(2_000) { conn.withClient { it.status() } }
                assertEquals(PlaybackState.Stop, st.state)
                assertEquals(4, st.queueVersion)
            } finally {
                conn.close()
                job.cancelAndJoin()
            }
        }
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
