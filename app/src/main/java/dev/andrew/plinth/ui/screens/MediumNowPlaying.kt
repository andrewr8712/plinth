package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.ui.components.Artwork
import dev.andrew.plinth.ui.components.ProgressHairline
import dev.andrew.plinth.ui.components.QueueBand
import dev.andrew.plinth.ui.components.SignalStrip
import dev.andrew.plinth.ui.components.Transport
import dev.andrew.plinth.ui.theme.PlinthColors
import dev.andrew.plinth.ui.vm.PlayerUi

/**
 * 600–839 dp: too narrow for a cover beside the text, too wide for a phone sheet.
 * So the spine stacks — cover, title, signal, transport — with the band beneath it.
 */
@Composable
fun MediumNowPlaying(
    ui: PlayerUi,
    queue: List<QueueItem> = emptyList(),
    art: ArtRepository? = null,
    onPlayPause: () -> Unit = {},
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Double) -> Unit = {},
    onQueueItem: (Int) -> Unit = {},
    onOpenQueue: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(PlinthColors.Base).padding(horizontal = 40.dp, vertical = 32.dp)) {
        Artwork(
            albumId = ui.albumId,
            firstTrackUri = ui.uri,
            art = art,
            modifier = Modifier.fillMaxWidth().height(400.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = ui.title.ifBlank { "Nothing playing" },
            color = PlinthColors.Text,
            style = MaterialTheme.typography.displayMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = ui.artist,
            color = PlinthColors.Muted,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        ui.strip?.let { SignalStrip(it) }
        Spacer(Modifier.height(24.dp))
        ProgressHairline(ui.elapsed, ui.duration, onSeek)
        Spacer(Modifier.height(16.dp))
        Transport(ui.isPlaying, ui.connected, onPrev, onPlayPause, onNext)
        Spacer(Modifier.height(16.dp))
        QueueBand(
            items = queue,
            onItemClick = onQueueItem,
            onOpenQueue = onOpenQueue,
            modifier = Modifier.fillMaxWidth().testTag("queue-band"),
        )
    }
}
