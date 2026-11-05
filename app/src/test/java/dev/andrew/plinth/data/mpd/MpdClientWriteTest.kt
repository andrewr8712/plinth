package dev.andrew.plinth.data.mpd

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MpdClientWriteTest {

    private lateinit var server: FakeMpdServer
    private lateinit var socket: MpdSocket

    @Before
    fun setUp() {
        server = FakeMpdServer().start()
        socket = MpdSocket()
        runBlocking { socket.connect("127.0.0.1", server.port) }
    }

    @After
    fun tearDown() {
        socket.close()
        server.close()
    }

    private fun client() = MpdClient(socket)

    @Test
    fun `pause with no argument toggles`() = runBlocking {
        client().pause()
        assertEquals("pause", server.lastCommand)
    }

    @Test
    fun `seek sends one shot with two decimals`() = runBlocking {
        client().seekCur(42.5)
        assertEquals("seekcur 42.50", server.lastCommand)
    }

    @Test
    fun `options send 1 for true`() = runBlocking {
        client().setSingle(true)
        assertEquals("single 1", server.lastCommand)
    }

    @Test
    fun `ACK from a write keeps its code so callers can tell it from a dead link`() {
        server.onCommand("play") { "ACK [2@0] {play} Bad song index\n" }
        val thrown = assertThrows(MpdException.Ack::class.java) {
            runBlocking { client().play(99) }
        }
        assertEquals(2, thrown.code)
        assertEquals("play", thrown.command)
        assertEquals("Bad song index", thrown.message)
    }
}
