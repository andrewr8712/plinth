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

    @Test(timeout = 20_000)
    fun `an ACK is a reply, not a dead link`() = runBlocking {
        server.onCommand("idle") { null }
        server.onCommand("status") { "state: stop\nplaylist: 4\nOK\n" }
        server.onCommand("play") { "ACK [2@0] {play} Bad song index\n" }
        server.onCommand("albumart") { "ACK [50@0] {albumart} No file exists\n" }

        val conn = MpdConnection(host = "127.0.0.1", port = server.port)
        val job = launch(Dispatchers.IO) { conn.run() }
        try {
            withTimeout(5_000) { conn.state.first { it is ConnectionState.Up } }

            // A command ACK used to be Protocol, and withClient demoted on both.
            val writeError = runCatching { conn.withClient { it.play(99) } }.exceptionOrNull()
            assertTrue("expected an Ack, got $writeError", writeError is MpdException.Ack)
            assertEquals(2, (writeError as MpdException.Ack).code)

            // 50 is the one that used to disconnect the app while browsing covers.
            val artError = runCatching { conn.withClient { it.albumArt("missing.flac") } }.exceptionOrNull()
            assertTrue("expected Ack 50, got $artError", artError is MpdException.Ack)
            assertEquals(50, (artError as MpdException.Ack).code)

            assertEquals("an ACK is not a connection failure", 0, conn.failures)
            assertTrue("an ACK must not demote the link", conn.state.value is ConnectionState.Up)
            val st = withTimeout(2_000) { conn.withClient { it.status() } }
            assertEquals(PlaybackState.Stop, st.state)
        } finally {
            conn.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun `backoff doubles and caps at thirty seconds`() {
        val b = Backoff(jitter = { 0.5 })
        assertEquals(1_000L, b.next(1))
        assertEquals(2_000L, b.next(2))
        assertEquals(4_000L, b.next(3))
        assertEquals(30_000L, b.next(20))
    }
}
