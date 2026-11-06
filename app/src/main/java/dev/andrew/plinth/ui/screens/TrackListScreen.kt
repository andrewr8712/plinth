package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.ui.components.TrackRow
import dev.andrew.plinth.ui.components.songMenu
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * A flat list of songs under one heading: an artist's tracks, a folder's contents.
 * Album detail has its own screen because it also has to show the cover.
 */
@Composable
fun TrackListScreen(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    onPlay: (Int) -> Unit = {},
    onQueue: (Track) -> Unit = {},
    onShowDetails: (Track) -> Unit = {},
    showAlbum: Boolean = false,
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
                text = listOf(subtitle, "${tracks.size} tracks")
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(Modifier.fillMaxSize().testTag("trackList")) {
            itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
                TrackRow(
                    title = track.title,
                    subtitle = if (showAlbum) track.album else track.artist.ifBlank { track.albumArtist },
                    durationSec = track.durationSec,
                    onClick = { onPlay(index) },
                    testTag = "list-track-row",
                    actions = songMenu(
                        onPlay = { onPlay(index) },
                        onQueue = { onQueue(track) },
                        onDetails = { onShowDetails(track) },
                    ),
                )
            }
        }
    }
}

/** A quiet text action, used for "Play all" / "Queue all" and the queue's own actions. */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "action-$label",
) {
    Text(
        text = label,
        color = if (enabled) PlinthColors.Accent else PlinthColors.Faint,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag(testTag),
    )
}

@Composable
fun ActionRow(vararg actions: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { it() }
    }
}
