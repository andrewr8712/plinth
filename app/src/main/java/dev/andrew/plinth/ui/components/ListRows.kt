package dev.andrew.plinth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.ui.theme.PlinthColors

/** A clock reading for a duration in seconds: 4:31, 1:02:07. */
fun clock(seconds: Int): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * One song in a list. Title first, then whatever identifies it — artist, album, or the
 * folder it came from — because a list of 1,866 titles is unreadable without context.
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    durationSec: Int,
    onClick: () -> Unit = {},
    number: Int? = null,
    testTag: String = "track-row",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (number != null) {
            Text(
                text = number.toString().padStart(2, '0'),
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = PlinthColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = clock(durationSec),
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
        )
        trailing?.invoke()
    }
}

/** A plain one-line row, used by the artist and folder lists. */
@Composable
fun ListRow(
    text: String,
    secondary: String = "",
    onClick: () -> Unit,
    testTag: String = "list-row",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .background(PlinthColors.Base)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                color = PlinthColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        trailing?.invoke()
    }
}
