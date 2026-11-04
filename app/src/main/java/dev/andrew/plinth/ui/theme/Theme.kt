package dev.andrew.plinth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PlinthScheme = darkColorScheme(
    primary = PlinthColors.Accent,
    onPrimary = PlinthColors.Base,
    background = PlinthColors.Base,
    onBackground = PlinthColors.Text,
    surface = PlinthColors.Plate,
    onSurface = PlinthColors.Text,
    surfaceVariant = PlinthColors.Plate,
    onSurfaceVariant = PlinthColors.Muted,
    error = PlinthColors.Danger,
)

/** Dark only. There is no light theme and no setting to ask for one. */
@Composable
fun PlinthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlinthScheme,
        typography = PlinthType,
        content = content,
    )
}
