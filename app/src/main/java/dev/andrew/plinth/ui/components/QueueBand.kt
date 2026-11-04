package dev.andrew.plinth.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * The foot band: what is coming, text only. Thumbnails would cost a decode per row
 * for information the cover above already gives.
 */
@Composable
fun QueueBand(
    items: List<QueueItem>,
    onItemClick: (Int) -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 56.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (items.isEmpty()) "Up next" else "Up next · ${items.size}",
                color = PlinthColors.Muted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clickable { onOpenQueue() }
                    .testTag("queueBandHeader"),
            )
        }

        if (items.isEmpty()) {
            Text(
                "Nothing queued",
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(items.take(MAX_ROWS), key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clickable { onItemClick(item.pos) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        color = PlinthColors.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.artist,
                        color = PlinthColors.Faint,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_ROWS = 3
