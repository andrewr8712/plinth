package dev.andrew.plinth.ui.components

/**
 * One line in a long-press menu. [key] is stable and becomes the test tag; [label] is
 * the words the owner reads. The three sets live here rather than in the screens so
 * every surface offers the same vocabulary.
 */
data class MenuAction(
    val key: String,
    val label: String,
    val onClick: () -> Unit,
)

/** A song (or a file standing in for one) anywhere in the library. */
fun songMenu(
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onDetails: () -> Unit,
): List<MenuAction> = listOf(
    MenuAction("play-song", "Play Song", onPlay),
    MenuAction("add-to-queue", "Add to Queue", onQueue),
    MenuAction("view-details", "View Details", onDetails),
)

/** An album in the grid: play it now, append it, or swap the queue without playing. */
fun albumMenu(
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onReplace: () -> Unit,
): List<MenuAction> = collectionMenu("play-album", "Play Album", onPlay, onQueue, onReplace)

/** A folder row. Same three verbs; only the first label names the container. */
fun folderMenu(
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onReplace: () -> Unit,
): List<MenuAction> = collectionMenu("play-folder", "Play Folder", onPlay, onQueue, onReplace)

private fun collectionMenu(
    playKey: String,
    playLabel: String,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onReplace: () -> Unit,
): List<MenuAction> = listOf(
    MenuAction(playKey, playLabel, onPlay),
    MenuAction("add-to-queue", "Add to Queue", onQueue),
    MenuAction("replace-queue", "Replace Queue", onReplace),
)
