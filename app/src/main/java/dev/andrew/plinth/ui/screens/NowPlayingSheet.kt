package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.ui.components.Artwork
import dev.andrew.plinth.ui.components.ProgressHairline
import dev.andrew.plinth.ui.components.SignalStrip
import dev.andrew.plinth.ui.components.Transport
import dev.andrew.plinth.ui.theme.PlinthColors
import dev.andrew.plinth.ui.vm.PlayerUi

/**
 * The phone's now playing, as a sheet over the library. No meter: a phone is where
 * you choose what plays, not where you watch it. The up-next row is the queue's door.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    ui: PlayerUi,
    queue: List<QueueItem> = emptyList(),
    art: ArtRepository? = null,
    onDismiss: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Double) -> Unit = {},
    onOpenQueue: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PlinthColors.Base,
        scrimColor = PlinthColors.Base.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        dragHandle = {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Close now playing" },
                ) {
                    Text("⌄", color = PlinthColors.Muted, fontSize = 20.sp)
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
                .testTag("now-playing-sheet"),
        ) {
            Artwork(
                albumId = ui.albumId,
                firstTrackUri = ui.uri,
                art = art,
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = ui.title.ifBlank { "Nothing playing" },
                color = PlinthColors.Text,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = ui.artist,
                color = PlinthColors.Muted,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            ui.strip?.let { SignalStrip(it) }
            Spacer(Modifier.height(16.dp))
            ProgressHairline(ui.elapsed, ui.duration, onSeek)
            Spacer(Modifier.height(8.dp))
            Transport(ui.isPlaying, ui.connected, onPrev, onPlayPause, onNext)
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (queue.isEmpty()) "Up next" else "Up next · ${queue.size}",
                color = PlinthColors.Muted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("upNextRow").padding(vertical = 12.dp),
            )
            queue.take(2).forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        text = item.title,
                        color = PlinthColors.Faint,
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
                    )
                }
            }
        }
    }
}
