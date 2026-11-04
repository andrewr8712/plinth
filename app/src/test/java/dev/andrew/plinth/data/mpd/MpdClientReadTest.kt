package dev.andrew.plinth.data.mpd

import dev.andrew.plinth.data.model.AudioFormat
import dev.andrew.plinth.data.model.PlaybackState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MpdClientReadTest {

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
    fun `status maps every field and tolerates missing elapsed`() = runBlocking {
        server.onCommand("status") {
            "repeat: 0\nrandom: 1\nsingle: 0\nconsume: 0\nplaylist: 7\nplaylistlength: 12\nstate: stop\nsong: 1\nsongid: 2\nOK\n"
        }
        val st = client().status()
        assertEquals(PlaybackState.Stop, st.state)
        assertEquals(2, st.songId)
        assertNull(st.elapsed)
        assertEquals(7, st.queueVersion)
        assertTrue(st.random)
    }

    @Test
    fun `status parses dsd and pcm formats`() {
        assertEquals(64, AudioFormat.parse("dsd64:2")!!.dsdRate)
        assertEquals(96000, AudioFormat.parse("96000:24:2")!!.sampleRate)
    }

    @Test
    fun `currentsong without Format returns a track with null format`() = runBlocking {
        server.onCommand("currentsong") { "file: A/01 - X.dsf\nTitle: X\nArtist: Y\nTime: 200\nOK\n" }
        val t = client().currentSong()!!
        assertNull(t.format)
        assertEquals("X", t.title)
        assertEquals(200, t.durationSec)
    }

    @Test
    fun `outputs parses dop attribute`() = runBlocking {
        server.onCommand("outputs") {
            "outputid: 0\noutputname: Topping DX5 II\nplugin: alsa\noutputenabled: 1\nattribute: dop=1\nOK\n"
        }
        assertTrue(client().outputs().single().dopAttribute)
    }
}
