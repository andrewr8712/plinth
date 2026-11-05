package dev.andrew.plinth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.loudness.StereoLevel
import dev.andrew.plinth.ui.theme.PlinthColors
import kotlin.math.exp

/**
 * A peak/RMS follower with a fast attack and a slow release, so the bar moves like a
 * needle instead of tracking every 200 ms frame as a step.
 */
class Ballistics(initial: Float = 0f) {

    var value: Float = initial
        private set

    /** Advances toward [target] over [dtMs] milliseconds and returns the new value. */
    fun step(current: Float, target: Float, dtMs: Long): Float {
        if (dtMs <= 0) return current
        val tau = if (target > current) ATTACK_MS else RELEASE_MS
        val alpha = 1f - exp(-dtMs / tau)
        value = current + (target - current) * alpha
        return value
    }

    private companion object {
        const val ATTACK_MS = 30f
        const val RELEASE_MS = 350f
    }
}

/**
 * The tablet's level meter: two bars, 16 dp wide and 56 dp tall, under a header that
 * carries the live value. With no frame for this position it draws a baseline and says
 * so — an animation with no data behind it is the one failure this feature exists to
 * avoid.
 */
@Composable
fun LevelMeter(
    level: StereoLevel?,
    active: Boolean,
    modifier: Modifier = Modifier,
    /** The streamer is unreachable: the bars have no subject at all, not just no frame. */
    offline: Boolean = false,
) {
    val target by rememberUpdatedState(level)
    var left by remember { mutableFloatStateOf(0f) }
    var right by remember { mutableFloatStateOf(0f) }
    val ballLeft = remember { Ballistics() }
    val ballRight = remember { Ballistics() }

    LaunchedEffect(active) {
        if (!active) {
            left = 0f
            right = 0f
            return@LaunchedEffect
        }
        var previous = 0L
        while (true) {
            // withInfiniteAnimationFrameNanos, not withFrameNanos: a plain frame loop
            // marks Compose permanently busy, so every instrumented test that composes
            // this meter times out on idling.
            withInfiniteAnimationFrameNanos { now ->
                if (previous != 0L) {
                    val dt = (now - previous) / 1_000_000
                    val frame = target
                    left = ballLeft.step(left, (frame?.left ?: 0) / 255f, dt)
                    right = ballRight.step(right, (frame?.right ?: 0) / 255f, dt)
                }
                previous = now
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = when {
                offline -> "No signal"
                level == null -> "No level data"
                else -> "LEVEL  ${level.dbfs} dBFS"
            },
            color = if (level == null || offline) PlinthColors.Faint else PlinthColors.Muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 6.dp).testTag("level-header"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = when {
                        offline -> "No signal: streamer offline"
                        level == null -> "No level data"
                        else -> "Stereo level meter"
                    }
                    // The bars are flattened while paused or offline; say so, so
                    // assistive tech does not read a still bar as a silent signal.
                    stateDescription = when {
                        offline -> "Offline"
                        level == null -> "No level data"
                        active -> "Live"
                        else -> "Idle"
                    }
                }
                .testTag("level-meter"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MeterBar(left, level == null || offline, Modifier.weight(1f))
            MeterBar(right, level == null || offline, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MeterBar(fraction: Float, unknown: Boolean, modifier: Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
        if (unknown) {
            // A still baseline at low alpha: present, obviously idle, never moving.
            drawRect(
                color = PlinthColors.Hair,
                topLeft = Offset(0f, size.height - 1.5f),
                size = Size(size.width, 1.5f),
            )
            return@Canvas
        }
        drawRect(color = PlinthColors.Hair, size = size)
        val filled = (size.height * fraction.coerceIn(0f, 1f))
        drawRect(
            color = PlinthColors.Accent,
            topLeft = Offset(0f, size.height - filled),
            size = Size(size.width, filled),
        )
    }
}
