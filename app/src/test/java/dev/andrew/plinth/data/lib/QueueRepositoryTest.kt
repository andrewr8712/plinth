package dev.andrew.plinth.data.mpd

import dev.andrew.plinth.data.lib.QueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueRepositoryTest {

    private lateinit var server: FakeMpdServer
    private lateinit var socket: MpdSocket
    private lateinit var repo: QueueRepository

    @Before
    fun setUp() {
        server = FakeMpdServer().start()
        socket = MpdSocket()
        runBlocking { socket.connect("127.0.0.1", server.port) }
        repo = QueueRepository(MpdClient(socket))
    }

    @After
    fun tearDown() {
        socket.close()
        server.close()
    }

    private fun item(id: Int, title: String) = listOf(
        "file" to "$title.flac",
        "Id" to id.toString(),
        "Title" to title,
        "Artist" to "Artist $title",
        "Album" to "Album",
        "Time" to "100",
    )

    private fun queueOf(vararg items: List<Pair<String, String>>) {
        // Pos is assigned by position, as MPD does
        server.queue(
            items.mapIndexed { index, block ->
                block.filter { it.first != "Pos" } + ("Pos" to index.toString())
            },
        )
    }

    @Test
    fun `refresh drops rows removed by another client`() = runBlocking {
        queueOf(item(1, "a"), item(2, "b"), item(3, "c"))
        repo.refresh()
        assertEquals(3, repo.items.value.size)

        // another client deleted two rows: playlistinfo never reports removals
        queueOf(item(2, "b"))
        repo.refresh()
        assertEquals(listOf(2), repo.items.value.map { it.id })
    }

    @Test
    fun `refresh keeps the server's field values`() = runBlocking {
        queueOf(item(7, "Teardrop"))
        repo.refresh()
        val row = repo.items.value.single()
        assertEquals(7, row.id)
        assertEquals(0, row.pos)
        assertEquals("Teardrop.flac", row.uri)
        assertEquals("Teardrop", row.title)
        assertEquals("Artist Teardrop", row.artist)
        assertEquals(100, row.durationSec)
    }

    @Test
    fun `move sends moveid with the id at that index and the target position`() = runBlocking {
        queueOf(item(11, "a"), item(22, "b"), item(33, "c"))
        repo.refresh()
        repo.move(0, 2)
        // MPD moves by song id; the id at index 0 is 11, and the target position is 2
        assertEquals("moveid 11 2", server.commands.last { it.startsWith("moveid") })
    }

    @Test
    fun `remove and clear use ids`() = runBlocking {
        queueOf(item(5, "a"))
        repo.refresh()
        repo.remove(5)
        assertEquals("deleteid 5", server.commands.last { it.startsWith("deleteid") })
        repo.clearAll()
        assertEquals("clear", server.commands.last { it == "clear" })
    }

    @Test
    fun `addToQueue escapes quotes in uris`() = runBlocking {
        repo.addToQueue("A \"quoted\"/x.flac")
        assertEquals("addid \"A \\\"quoted\\\"/x.flac\"", server.commands.last { it.startsWith("addid") })
    }

    @Test
    fun `playAt sends the position`() = runBlocking {
        repo.playAt(4)
        assertEquals("play 4", server.lastCommand)
    }

    @Test
    fun `playlists are listed and loaded by name`() = runBlocking {
        server.onCommand("listplaylists") { "playlist: Late night\nplaylist: Sunday\nOK\n" }
        assertEquals(listOf("Late night", "Sunday"), repo.playlists())

        repo.saveAs("Late night")
        assertTrue(server.commands.contains("save \"Late night\""))

        repo.load("Sunday")
        assertTrue(server.commands.contains("load \"Sunday\""))
    }
}
