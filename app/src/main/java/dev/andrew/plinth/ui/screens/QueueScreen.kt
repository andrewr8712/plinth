package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.ui.components.ListRow
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * The queue as a list you can edit. Every edit goes to the server and the whole list is
 * refetched, because `plchanges` cannot report a removal.
 */
@Composable
fun QueueScreen(
    items: List<QueueItem>,
    currentId: Int? = null,
    playlists: List<String> = emptyList(),
    onPlay: (Int) -> Unit = {},
    onRemove: (Int) -> Unit = {},
    onMove: (Int, Int) -> Unit = { _, _ -> },
    onClear: () -> Unit = {},
    onSave: (String) -> Unit = {},
    onLoad: (String) -> Unit = {},
) {
    var saving by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(PlinthColors.Base)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Queue",
                color = PlinthColors.Text,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${items.size} tracks",
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (items.isNotEmpty()) {
            ActionRow(
                { TextAction("Save", onClick = { saving = true }, testTag = "queue-save") },
                { TextAction("Load", onClick = { loading = true }, testTag = "queue-load") },
                { TextAction("Clear", onClick = onClear, testTag = "queue-clear") },
            )
        } else {
            ActionRow(
                { TextAction("Load", onClick = { loading = true }, testTag = "queue-load") },
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "The queue is empty",
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().testTag("queueList")) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    QueueRow(
                        item = item,
                        index = index,
                        last = index == items.lastIndex,
                        current = item.id == currentId,
                        onPlay = { onPlay(index) },
                        onRemove = { onRemove(item.id) },
                        onUp = { onMove(index, index - 1) },
                        onDown = { onMove(index, index + 1) },
                    )
                }
            }
        }
    }

    if (saving) {
        SaveDialog(
            onDismiss = { saving = false },
            onSave = { name ->
                saving = false
                onSave(name)
            },
        )
    }

    if (loading) {
        LoadDialog(
            playlists = playlists,
            onDismiss = { loading = false },
            onLoad = { name ->
                loading = false
                onLoad(name)
            },
        )
    }
}

@Composable
private fun LoadDialog(playlists: List<String>, onDismiss: () -> Unit, onLoad: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlinthColors.Plate,
        title = { Text("Load playlist", color = PlinthColors.Text) },
        text = {
            if (playlists.isEmpty()) {
                Text("No saved playlists on the streamer.", color = PlinthColors.Faint)
            } else {
                LazyColumn(Modifier.testTag("playlist-list")) {
                    items(playlists, key = { it }) { name ->
                        ListRow(
                            text = name,
                            onClick = { onLoad(name) },
                            testTag = "playlist-row",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = PlinthColors.Accent) }
        },
    )
}

@Composable
private fun QueueRow(
    item: QueueItem,
    index: Int,
    last: Boolean,
    current: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlinthColors.Base)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag("queue-row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = if (current) PlinthColors.Accent else PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
        )
        Column(
            Modifier
                .weight(1f)
                .testTag("queue-row-body")
                .clickable { onPlay() },
        ) {
            Text(
                text = item.title.ifBlank { item.uri.substringAfterLast('/') },
                color = if (current) PlinthColors.Accent else PlinthColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(item.artist, item.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RowAction("↑", "Move up", enabled = index > 0, onClick = onUp)
        RowAction("↓", "Move down", enabled = !last, onClick = onDown)
        RowAction("×", "Remove from queue", onClick = onRemove)
    }
}

@Composable
private fun RowAction(
    glyph: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = glyph,
        color = if (enabled) PlinthColors.Muted else PlinthColors.Hair,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun SaveDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlinthColors.Plate,
        title = { Text("Save queue as playlist", color = PlinthColors.Text) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name", color = PlinthColors.Faint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PlinthColors.Base,
                    unfocusedContainerColor = PlinthColors.Base,
                    focusedTextColor = PlinthColors.Text,
                    unfocusedTextColor = PlinthColors.Text,
                    cursorColor = PlinthColors.Accent,
                ),
                modifier = Modifier.testTag("playlist-name"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text("Save", color = PlinthColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PlinthColors.Faint) }
        },
    )
}
