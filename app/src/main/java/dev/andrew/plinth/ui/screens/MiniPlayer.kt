package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.ui.components.Artwork
import dev.andrew.plinth.ui.theme.PlinthColors
import dev.andrew.plinth.ui.vm.PlayerUi

/**
 * The phone's persistent strip above the navigation bar. Tapping anywhere but the
 * play button opens the full sheet; the button acts without leaving the library.
 */
@Composable
fun MiniPlayer(
    ui: PlayerUi,
    art: ArtRepository?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(PlinthColors.Plate)
            .clickable { onOpen() }
            .padding(horizontal = 24.dp)
            .testTag("mini-player"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            albumId = ui.albumId,
            firstTrackUri = ui.uri,
            art = art,
            label = ui.album.ifBlank { ui.title },
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = ui.title.ifBlank { "Nothing playing" },
                color = PlinthColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ui.artist,
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.semantics { contentDescription = if (ui.isPlaying) "Pause" else "Play" },
        ) {
            Text(
                text = if (ui.isPlaying) "⏸" else "▶",
                color = PlinthColors.Accent,
                fontSize = 20.sp,
            )
        }
    }
}
