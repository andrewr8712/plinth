package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * One album, in disc then track order. A row is played by tapping it — which is the
 * only point of the screen, so the row is the target, not a play button beside it.
 */
@Composable
fun AlbumScreen(
    title: String,
    artist: String,
    tracks: List<Track>,
    onPlayTrack: (Int) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(PlinthColors.Base)) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
            Text(
                text = title,
                color = PlinthColors.Text,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Untagged rips have no artist, so the separator must not lead the line.
                text = listOf(artist, "${tracks.size} tracks").filter { it.isNotBlank() }.joinToString(" · "),
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(Modifier.fillMaxSize().testTag("trackList")) {
            itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onPlayTrack(index) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        // No Track tag is common on these DFF rips; printing 00 would be a
                        // fabricated number, so the column stays empty instead.
                        text = if (track.trackNo > 0) track.trackNo.toString().padStart(2, '0') else "",
                        color = PlinthColors.Faint,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                    Text(
                        text = track.title,
                        color = PlinthColors.Text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = clock(track.durationSec),
                        color = PlinthColors.Faint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
