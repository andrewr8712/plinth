package dev.andrew.plinth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * The one failure that is worth a control: the streamer is not answering. It says why
 * in the owner's terms and offers the only useful action, a redial that does not wait
 * out the backoff. Transport stays visible and disabled rather than disappearing.
 */
@Composable
fun UnavailableChip(
    reason: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(PlinthColors.Plate)
            .padding(start = 14.dp, end = 4.dp)
            .testTag("offline-chip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(7.dp).background(PlinthColors.Danger, CircleShape))
        Text(
            text = reason,
            color = PlinthColors.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.testTag("offline-retry"),
        ) {
            Text(
                text = "Retry now",
                color = PlinthColors.Text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
