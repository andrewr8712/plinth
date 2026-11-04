package dev.andrew.plinth.data.mpd

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MpdSocketTest {

    private lateinit var server: FakeMpdServer

    @Before
    fun setUp() {
        server = FakeMpdServer().start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun socket(): MpdSocket = MpdSocket()

    @Test
    fun `reads until terminator, not until substring`() = runBlocking {
        // 12-byte body whose middle contains a literal "OK\n", then the terminator
        server.onCommand("albumart") { "size: 12\nbinary: 12\n\u0000OK\n\u0000\u0000\u0000\u0000\u0000OK\n\nOK\n" }
        val s = socket()
        s.connect("127.0.0.1", server.port)
        val r = s.commandBinary("albumart \"x\" 0", 5_000)
        assertEquals(12, r.bytes.size)
        assertArrayEquals(
            byteArrayOf(0, 'O'.code.toByte(), 'K'.code.toByte(), '\n'.code.toByte(), 0, 0, 0, 0, 0, 'O'.code.toByte(), 'K'.code.toByte(), '\n'.code.toByte()),
            r.bytes,
        )
        s.close()
    }

    @Test
    fun `decodes UTF-8 tag bytes, not Latin-1`() = runBlocking {
        server.onCommand("currentsong") {
            "file: BIHQVP~2/07. L’AMOUR DE MA VIE.flac\nGenre: Pop, Rock, Alternatif et Indé\nOK\n"
        }
        val s = socket()
        s.connect("127.0.0.1", server.port)
        val pairs = (s.command("currentsong") as MpdReply.Ok).pairs
        s.close()
        assertEquals("BIHQVP~2/07. L’AMOUR DE MA VIE.flac", pairs.first { it.first == "file" }.second)
        assertEquals("Pop, Rock, Alternatif et Indé", pairs.first { it.first == "Genre" }.second)
    }

    @Test
    fun `joins multiple binary chunks`() = runBlocking {
        server.onCommand("albumart") { "size: 6\nbinary: 3\nabc\nbinary: 3\ndef\nOK\n" }
        val s = socket()
        s.connect("127.0.0.1", server.port)
        val r = s.commandBinary("albumart \"x\" 0", 5_000)
        s.close()
        assertEquals(6, r.size)
        assertEquals("abcdef", String(r.bytes))
    }

    @Test
    fun `treats a bare OK binary reply as no data`() = runBlocking {
        // what readpicture returns on this streamer for folder-art albums
        server.onCommand("readpicture") { "OK\n" }
        val s = socket()
        s.connect("127.0.0.1", server.port)
        val r = s.commandBinary("readpicture \"x\" 0", 2_000)
        s.close()
        assertEquals(0, r.bytes.size)
    }

    @Test(timeout = 10_000)
    fun `a timed out reply kills the socket instead of desynchronising it`() = runBlocking {
        server.stall = true
        val s = socket()
        s.connect("127.0.0.1", server.port)
        val started = System.currentTimeMillis()
        var first: Throwable? = null
        try {
            s.command("status", timeoutMs = 400)
        } catch (t: Throwable) {
            first = t
        }
        assertTrue("expected a timeout, got $first", first is MpdException.Timeout)
        assertTrue("should give up near the timeout", System.currentTimeMillis() - started < 3_000)

        // the half-read reply cannot be trusted: the next call must refuse, not parse junk
        var second: Throwable? = null
        try {
            s.command("status", timeoutMs = 400)
        } catch (t: Throwable) {
            second = t
        }
        assertTrue("expected Disconnected, got $second", second is MpdException.Disconnected)
        assertTrue("socket must be closed", !s.isOpen)
    }

    @Test
    fun `surfaces ACK as MpdReply Ack`() = runBlocking {
        server.onCommand("setvol") { "ACK [5@0] {setvol} No mixer\n" }
        val s = socket()
        s.connect("127.0.0.1", server.port)
        val reply = s.command("setvol 10")
        s.close()
        assertTrue("expected ACK, got $reply", reply is MpdReply.Ack)
        assertEquals("No mixer", (reply as MpdReply.Ack).message)
    }

    @Test
    fun `binary ACK keeps its code so callers can act on 50`() = runBlocking {
        server.onCommand("albumart") { "ACK [50@0] {albumart} No file exists\n" }
        val s = socket()
        s.connect("127.0.0.1", server.port)
        var thrown: Throwable? = null
        try {
            s.commandBinary("albumart \"x\" 0", 2_000)
        } catch (t: Throwable) {
            thrown = t
        }
        s.close()
        assertTrue("expected MpdException.Ack, got $thrown", thrown is MpdException.Ack)
        assertEquals(50, (thrown as MpdException.Ack).code)
    }

    @Test
    fun `rejects a banner that is not MPD`() = runBlocking {
        server.banner = "HTTP/1.1 400 Bad Request"
        val s = socket()
        var thrown: Throwable? = null
        try {
            s.connect("127.0.0.1", server.port)
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue("expected Protocol, got $thrown", thrown is MpdException.Protocol)
        assertTrue("a refused banner must not leave the socket open", !s.isOpen)
    }
}
