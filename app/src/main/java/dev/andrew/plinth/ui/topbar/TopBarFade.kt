package dev.andrew.plinth.ui.topbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * The tablet top bar gets out of the way when nobody is using it: it stays opaque
 * while the idle clock is short, then settles to a low — but still legible — alpha.
 */
internal object TopBarFade {
    /** How long the bar stays fully visible after the last interaction. */
    const val IDLE_THRESHOLD_MS = 4_000L

    /** The fade itself, inside the design's 400–600 ms window. */
    const val FADE_MILLIS = 500

    /** Low enough to recede, high enough to read the labels. */
    const val RESTING_ALPHA = 0.22f

    const val FULL_ALPHA = 1f
}

/**
 * The visibility policy, kept free of Compose so a JVM test can pin the edges without
 * a device. Idle time below the threshold is fully opaque; at or beyond it the bar
 * rests at [TopBarFade.RESTING_ALPHA]. An interaction resets the clock to zero, which
 * is why this same function answers full opacity for a fresh press.
 */
internal fun topBarAlphaAfter(idleMs: Long): Float =
    if (idleMs < TopBarFade.IDLE_THRESHOLD_MS) TopBarFade.FULL_ALPHA
    else TopBarFade.RESTING_ALPHA

/**
 * The bar's alpha for the current route. Keying the effect on both [route] and
 * [interactionSignal] means a navigation or a touch cancels any running fade, snaps
 * the bar opaque, and starts the idle countdown again from zero.
 */
@Composable
internal fun rememberTopBarAlpha(route: String?, interactionSignal: Int): Float {
    val alpha = remember { Animatable(TopBarFade.FULL_ALPHA) }
    LaunchedEffect(route, interactionSignal) {
        alpha.snapTo(TopBarFade.FULL_ALPHA)
        delay(TopBarFade.IDLE_THRESHOLD_MS)
        alpha.animateTo(
            targetValue = topBarAlphaAfter(TopBarFade.IDLE_THRESHOLD_MS),
            animationSpec = tween(durationMillis = TopBarFade.FADE_MILLIS),
        )
    }
    return alpha.value
}

/**
 * Reports pointer and key activity anywhere inside this modifier's subtree without
 * taking it from anyone: the Initial pass is observed before children's gesture
 * detectors get the Main pass, and nothing here is consumed or delayed, so clicks,
 * the queue's scroll and the bar's own buttons behave exactly as before.
 *
 * Press and release are the signal, not every move: a tap, drag and scroll all begin
 * with a press, and reporting each move would restart the idle countdown on every
 * frame of a scroll. Wheel/trackpad scrolls are reported too, and key events are seen
 * in the preview pass (returning false lets them on to the focused field).
 */
@Composable
internal fun Modifier.observeTopBarInteraction(onInteraction: () -> Unit): Modifier {
    val latest by rememberUpdatedState(onInteraction)
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val type = awaitPointerEvent(PointerEventPass.Initial).type
                    if (type == PointerEventType.Press ||
                        type == PointerEventType.Release ||
                        type == PointerEventType.Scroll
                    ) {
                        latest()
                    }
                }
            }
        }
        .onPreviewKeyEvent {
            latest()
            false
        }
}
