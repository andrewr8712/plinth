package dev.andrew.plinth.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * Three controls, play at the exact centre. Previous and next live in the start and
 * end slots, so nothing about the play button's position depends on the title length.
 */
@Composable
fun Transport(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(modifier = Modifier.align(Alignment.CenterStart)) {
            IconButton(
                onClick = onPrev,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Previous" },
            ) {
                Text("⏮", color = if (enabled) PlinthColors.Text else PlinthColors.Faint, fontSize = 22.sp)
            }
        }

        IconButton(
            onClick = onPlayPause,
            enabled = enabled,
            modifier = Modifier
                .size(72.dp)
                .testTag("playPause")
                .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶",
                color = if (enabled) PlinthColors.Accent else PlinthColors.Faint,
                fontSize = 30.sp,
            )
        }

        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            IconButton(
                onClick = onNext,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Next" },
            ) {
                Text("⏭", color = if (enabled) PlinthColors.Text else PlinthColors.Faint, fontSize = 22.sp)
            }
        }
    }
}
