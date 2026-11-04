package dev.andrew.plinth.data.lib

import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.data.mpd.MpdClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The queue, as a whole. `plchanges` never reports removals, so any edit — by this
 * app or another client — is answered by refetching `playlistinfo` outright. A diff
 * would silently keep rows that no longer exist.
 */
class QueueRepository(private val client: MpdClient) {

    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    suspend fun refresh() {
        _items.value = client.playlistInfo()
    }

    suspend fun playAt(pos: Int) = client.play(pos)

    suspend fun remove(id: Int) {
        client.deleteId(id)
        refresh()
    }

    /** [from] and [to] are list indices; MPD moves by song id. */
    suspend fun move(from: Int, to: Int) {
        val id = _items.value.getOrNull(from)?.id ?: return
        client.moveId(id, to)
        refresh()
    }

    suspend fun clearAll() {
        client.clearQueue()
        refresh()
    }

    suspend fun addToQueue(uri: String) {
        client.addId(uri)
        refresh()
    }

    suspend fun saveAs(name: String) = client.savePlaylist(name)

    suspend fun load(name: String) {
        client.loadPlaylist(name)
        refresh()
    }

    suspend fun playlists(): List<String> = client.listPlaylists()
}
