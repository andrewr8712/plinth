package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.ui.components.Artwork
import dev.andrew.plinth.ui.components.TrackRow
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * One album, in disc then track order. A row is played by tapping it; "play all" is the
 * same thing from the top, and "queue all" appends instead of replacing the queue.
 */
@Composable
fun AlbumScreen(
    title: String,
    artist: String,
    tracks: List<Track>,
    albumId: String = "",
    art: ArtRepository? = null,
    onPlayTrack: (Int) -> Unit = {},
    onPlayAll: () -> Unit = {},
    onQueueAll: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(PlinthColors.Base)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Artwork(
                albumId = albumId,
                firstTrackUri = tracks.firstOrNull()?.uri.orEmpty(),
                art = art,
                label = title,
                modifier = Modifier.size(132.dp).testTag("album-cover"),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = PlinthColors.Text,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Untagged rips have no artist, so the separator must not lead the line.
                    text = listOf(artist, "${tracks.size} tracks")
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextAction("Play all", onClick = onPlayAll, testTag = "play-all")
                    TextAction("Queue all", onClick = onQueueAll, testTag = "queue-all")
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize().testTag("trackList")) {
            itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
                TrackRow(
                    title = track.title,
                    subtitle = track.artist,
                    durationSec = track.durationSec,
                    onClick = { onPlayTrack(index) },
                    // No Track tag is common on these DFF rips; printing 00 would be a
                    // fabricated number, so the column stays empty instead.
                    number = track.trackNo.takeIf { it > 0 },
                    testTag = "list-track-row",
                )
            }
        }
    }
}
