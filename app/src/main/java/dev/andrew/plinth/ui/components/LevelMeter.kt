package dev.andrew.plinth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * Two channels of level. Deliberately inert until the loudness envelope lands
 * (Task 17): inventing levels would be worse than showing a flat line, so the bars
 * sit at their floor and the label says the meter has nothing to draw.
 */
@Composable
fun LevelMeter(
    left: Float = 0f,
    right: Float = 0f,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { contentDescription = "Stereo level meter" },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            val barWidth = size.width - 2f
            val half = size.height / 2
            listOf(left, right).forEachIndexed { index, level ->
                val y = half * index + half / 2
                drawLine(
                    color = if (enabled) PlinthColors.Hair else PlinthColors.Hair.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(barWidth, y),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
                if (enabled) {
                    drawLine(
                        color = PlinthColors.Accent,
                        start = Offset(0f, y),
                        end = Offset(barWidth * level.coerceIn(0f, 1f), y),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
