package dev.andrew.plinth.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One accent, one near-black. The muted text sits at ~5.2:1 on Base so it survives
 * a dim room without going grey-mush.
 */
object PlinthColors {
    val Base = Color(0xFF0A0A0C)
    val Plate = Color(0xFF17171B)
    val Text = Color(0xFFF6F5F3)
    val Muted = Text.copy(alpha = 0.62f)
    val Faint = Text.copy(alpha = 0.52f)
    val Hair = Text.copy(alpha = 0.10f)
    val Accent = Color(0xFF7ED9C3)
    val Danger = Color(0xFFE0574F)
}
