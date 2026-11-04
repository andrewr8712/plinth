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
        // 12-byte body whose middle contains a literal "OK\n"
        // 12-byte body, then the protocol's own terminator after it
        server.onCommand("albumart") { "size: 12\nbinary: 12\n\u0000OK\n\u0000\u0000\u0000\u0000\u0000OK\nOK\n" }
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
    fun `times out when the server accepts and never answers`() = runBlocking {
        server.stall = true
        val s = socket()
        s.connect("127.0.0.1", server.port)
        val started = System.currentTimeMillis()
        var thrown: Throwable? = null
        try {
            s.command("status", timeoutMs = 400)
        } catch (t: Throwable) {
            thrown = t
        }
        s.close()
        assertTrue("expected a timeout, got $thrown", thrown is MpdException.Timeout)
        assertTrue("should give up near the timeout", System.currentTimeMillis() - started < 3_000)
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
}
