package dev.andrew.plinth.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * A hairline, not a slider: it thickens under the finger and sends **one** seek on
 * release. Seeking per drag would queue a command for every pixel of movement.
 */
@Composable
fun ProgressHairline(
    elapsed: Double?,
    duration: Double?,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val total = duration?.takeIf { it > 0.0 }
    val baseFraction = if (total != null && elapsed != null) (elapsed / total).toFloat() else 0f
    val shown = if (dragFraction >= 0f) dragFraction else baseFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .testTag("progressHairline")
            .pointerInput(total) {
                if (total == null) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val fraction = dragFraction.coerceIn(0f, 1f)
                        dragFraction = -1f
                        onSeek(fraction * total)
                    },
                    onDragCancel = { dragFraction = -1f },
                )
            }
            .drawBehind {
                val y = size.height / 2
                drawLine(
                    color = PlinthColors.Hair,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = PlinthColors.Accent,
                    start = Offset(0f, y),
                    end = Offset(size.width * shown.coerceIn(0f, 1f), y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            },
    )
}
