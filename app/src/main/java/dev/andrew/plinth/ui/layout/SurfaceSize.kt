package dev.andrew.plinth.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * How much room the app has, not what device it is on: the same tablet is Expanded
 * in landscape and may be Compact in a split-screen window beside something else.
 */
enum class SurfaceSize { Compact, Medium, Expanded }

fun surfaceSizeFor(widthDp: Float): SurfaceSize = when {
    widthDp < 600f -> SurfaceSize.Compact
    widthDp < 840f -> SurfaceSize.Medium
    else -> SurfaceSize.Expanded
}

@Composable
fun rememberSurfaceSize(): SurfaceSize {
    val configuration = LocalConfiguration.current
    return surfaceSizeFor(configuration.screenWidthDp.toFloat())
}
