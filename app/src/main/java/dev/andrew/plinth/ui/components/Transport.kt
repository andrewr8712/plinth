package dev.andrew.plinth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
                TransportGlyph(Glyph.Prev, if (enabled) PlinthColors.Text else PlinthColors.Faint, 22.dp)
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
            TransportGlyph(
                glyph = if (isPlaying) Glyph.Pause else Glyph.Play,
                color = if (enabled) PlinthColors.Accent else PlinthColors.Faint,
                size = 30.dp,
            )
        }

        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            IconButton(
                onClick = onNext,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Next" },
            ) {
                TransportGlyph(Glyph.Next, if (enabled) PlinthColors.Text else PlinthColors.Faint, 22.dp)
            }
        }
    }
}

private enum class Glyph { Prev, Play, Pause, Next }

/** The drawn play/pause mark, shared with the phone's mini player. */
@Composable
fun PlayPauseGlyph(isPlaying: Boolean, color: Color, size: Dp) {
    TransportGlyph(if (isPlaying) Glyph.Pause else Glyph.Play, color, size)
}

/**
 * Drawn, not typed. U+23F8/23EE/23ED have emoji presentation and render as coloured
 * system emoji (a blue pause block) on Android; drawing keeps the glyph on-palette.
 */
@Composable
private fun TransportGlyph(glyph: Glyph, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bar = w * 0.18f
        val pad = w * 0.06f
        when (glyph) {
            Glyph.Play -> drawPath(
                Path().apply {
                    moveTo(pad * 2, h * 0.08f)
                    lineTo(w - pad * 2, h / 2f)
                    lineTo(pad * 2, h * 0.92f)
                    close()
                },
                color,
            )
            Glyph.Pause -> {
                drawRect(color, Offset(w * 0.24f, h * 0.08f), Size(bar, h * 0.84f))
                drawRect(color, Offset(w * 0.58f, h * 0.08f), Size(bar, h * 0.84f))
            }
            Glyph.Prev -> {
                drawRect(color, Offset(pad, h * 0.12f), Size(bar * 0.9f, h * 0.76f))
                drawPath(
                    Path().apply {
                        moveTo(w - pad, h * 0.12f)
                        lineTo(w * 0.32f, h / 2f)
                        lineTo(w - pad, h * 0.88f)
                        close()
                    },
                    color,
                )
            }
            Glyph.Next -> {
                drawPath(
                    Path().apply {
                        moveTo(pad, h * 0.12f)
                        lineTo(w * 0.68f, h / 2f)
                        lineTo(pad, h * 0.88f)
                        close()
                    },
                    color,
                )
                drawRect(color, Offset(w * 0.82f, h * 0.12f), Size(bar * 0.9f, h * 0.76f))
            }
        }
    }
}
