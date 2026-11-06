package dev.andrew.plinth.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The words and keys every long-press menu offers, pinned so surfaces cannot drift. */
class MenuActionsTest {

    @Test
    fun aSongOffersPlayQueueAndDetails() {
        val calls = mutableListOf<String>()
        val menu = songMenu(
            onPlay = { calls += "play" },
            onQueue = { calls += "queue" },
            onDetails = { calls += "details" },
        )

        assertEquals(listOf("play-song", "add-to-queue", "view-details"), menu.map { it.key })
        assertEquals(listOf("Play Song", "Add to Queue", "View Details"), menu.map { it.label })

        menu.forEach { it.onClick() }
        assertEquals(listOf("play", "queue", "details"), calls)
    }

    @Test
    fun anAlbumOffersPlayQueueAndReplace() {
        val calls = mutableListOf<String>()
        val menu = albumMenu(
            onPlay = { calls += "play" },
            onQueue = { calls += "queue" },
            onReplace = { calls += "replace" },
        )

        assertEquals(listOf("play-album", "add-to-queue", "replace-queue"), menu.map { it.key })
        assertEquals(listOf("Play Album", "Add to Queue", "Replace Queue"), menu.map { it.label })

        menu.forEach { it.onClick() }
        assertEquals(listOf("play", "queue", "replace"), calls)
    }

    @Test
    fun aFolderNamesItselfInTheFirstAction() {
        val menu = folderMenu(onPlay = {}, onQueue = {}, onReplace = {})

        assertEquals(listOf("play-folder", "add-to-queue", "replace-queue"), menu.map { it.key })
        assertEquals(listOf("Play Folder", "Add to Queue", "Replace Queue"), menu.map { it.label })
    }
}
