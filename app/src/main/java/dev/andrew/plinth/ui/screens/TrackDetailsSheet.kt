package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.ui.components.DetailRow
import dev.andrew.plinth.ui.components.cachedTagRows
import dev.andrew.plinth.ui.components.fileDetailRows
import dev.andrew.plinth.ui.components.tagDetailRows
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * One file's facts: what is on disk, then every tag the streamer reported for it.
 * The tags are read on demand rather than taken from the cache, because the cache only
 * keeps the fields the library needs and drops the rest (composer, comment, and so on).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailsSheet(
    track: Track,
    tags: List<Pair<String, String>>?,
    loading: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
) {
    val fileRows = fileDetailRows(track, tags)
    val tagRows = if (tags != null) tagDetailRows(tags) else cachedTagRows(track)
    val note = when {
        loading -> "Reading tags…"
        tags == null && failed -> "Streamer not answering — cached tags shown."
        tags != null && tagRows.isEmpty() -> "No tags on this file."
        tags == null -> "Showing cached tags."
        else -> null
    }

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
                        .semantics { contentDescription = "Close details" },
                ) {
                    Text("×", color = PlinthColors.Muted, fontSize = 22.sp)
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                .testTag("track-details"),
        ) {
            Text(
                text = "Details",
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = track.title.ifBlank { track.uri.substringAfterLast('/') },
                color = PlinthColors.Text,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            val under = listOf(track.artist, track.album).filter { it.isNotBlank() }
            if (under.isNotEmpty()) {
                Text(
                    text = under.joinToString(" · "),
                    color = PlinthColors.Muted,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DetailHeading("File")
            fileRows.forEach { DetailLine(it) }

            DetailHeading("Tags")
            note?.let {
                Text(
                    text = it,
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("details-note"),
                )
            }
            tagRows.forEach { DetailLine(it) }
        }
    }
}

@Composable
private fun DetailHeading(text: String) {
    Text(
        text = text.uppercase(),
        color = PlinthColors.Faint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun DetailLine(row: DetailRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("detail-${row.label.lowercase().replace(' ', '-')}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = row.label,
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = row.value,
            color = PlinthColors.Text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}
