package dev.andrew.plinth.data.art

import dev.andrew.plinth.data.mpd.FakeMpdServer
import dev.andrew.plinth.data.mpd.MpdClient
import dev.andrew.plinth.data.mpd.MpdSocket
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: FakeMpdServer
    private lateinit var socket: MpdSocket
    private lateinit var repo: ArtRepository

    @Before
    fun setUp() {
        server = FakeMpdServer().start()
        socket = MpdSocket()
        runBlocking { socket.connect("127.0.0.1", server.port) }
        repo = ArtRepository(MpdClient(socket), tmp.root)
    }

    @After
    fun tearDown() {
        socket.close()
        server.close()
    }

    @Test
    fun `ack 50 caches a negative that survives a new repository instance`() = runBlocking {
        server.onCommand("albumart") { "ACK [50@0] {albumart} No file exists\n" }
        assertEquals(ArtResult.None, repo.forAlbum("id1", "x.flac"))
        assertTrue(repo.negativeCached("id1"))

        val fresh = ArtRepository(MpdClient(socket), tmp.root)
        assertTrue("the marker must outlive the object", fresh.negativeCached("id1"))
    }

    @Test(timeout = 30_000)
    fun `timeout does not cache a negative`() = runBlocking {
        server.stall = true
        val result = repo.forAlbum("id2", "x.flac")
        assertTrue("expected Failed, got $result", result is ArtResult.Failed)
        assertFalse("a timeout is not evidence of missing art", repo.negativeCached("id2"))
    }

    @Test
    fun `jpeg with terminator bytes inside is stored whole`() = runBlocking {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + "\nOK\n".toByteArray() + ByteArray(200)
        server.binaryResponse(jpeg)
        val r = repo.forAlbum("id3", "x.flac") as ArtResult.Jpeg
        // 2 SOI bytes + literal "\nOK\n" + 200 filler = 206 (the plan's test said 207)
        assertEquals(206, r.bytes.size)
        assertArrayEquals(jpeg, r.bytes)
        assertEquals("second call comes from disk", 206, repo.cachedFile("id3")!!.length().toInt())
    }

    @Test
    fun `cached bytes are served without another request`() = runBlocking {
        server.binaryResponse(byteArrayOf(1, 2, 3))
        repo.forAlbum("id4", "x.flac")
        server.binaryResponse(byteArrayOf(9, 9, 9))
        val second = repo.forAlbum("id4", "x.flac") as ArtResult.Jpeg
        assertEquals(3, second.bytes.size)
        assertEquals(1, second.bytes[0].toInt())
    }

    @Test
    fun `negative older than seven days is retried`() {
        val marker = File(tmp.root, "art/none-old").apply { parentFile.mkdirs() }
        marker.writeText("")
        marker.setLastModified(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1_000)
        assertFalse(repo.negativeCached("old"))
    }

    @Test
    fun `a fresh negative is still a negative`() {
        val marker = File(tmp.root, "art/none-new").apply { parentFile.mkdirs() }
        marker.writeText("")
        assertTrue(repo.negativeCached("new"))
    }

    @Test
    fun `a different ack is a failure, not a negative`() = runBlocking {
        server.onCommand("albumart") { "ACK [2@0] {albumart} Bad song index\n" }
        val r = repo.forAlbum("id5", "x.flac")
        assertTrue("expected Failed, got $r", r is ArtResult.Failed)
        assertFalse(repo.negativeCached("id5"))
    }

    @Test
    fun `the chunk limit is raised before the first fetch`() = runBlocking {
        server.onCommand("albumart") { "ACK [50@0] {albumart} No file exists\n" }
        repo.forAlbum("id6", "x.flac")
        repo.forAlbum("id7", "y.flac")
        assertEquals(1, server.commands.count { it.startsWith("binarylimit") })
    }
}
