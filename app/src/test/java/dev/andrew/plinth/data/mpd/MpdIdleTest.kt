package dev.andrew.plinth.data.mpd

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MpdIdleTest {

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

    @Test
    fun `returns the deduped set of changed subsystems`() = runBlocking {
        server.onCommand("idle") { "changed: player\nchanged: mixer\nchanged: player\nOK\n" }
        assertEquals(setOf("player", "mixer"), MpdIdle(socket).awaitChange())
    }
}
