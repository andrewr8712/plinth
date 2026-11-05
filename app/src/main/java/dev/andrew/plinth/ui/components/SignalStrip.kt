package dev.andrew.plinth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrew.plinth.data.model.StripCells
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * Source / Link / Verdict. Three cells, tabular figures, one accented word when the
 * chain is doing nothing at all to the file.
 */
@Composable
fun SignalStrip(
    cells: StripCells,
    modifier: Modifier = Modifier,
    accentVerdict: Boolean = cells.verdict == "Bit-perfect",
    /** Offline has no evidence for any of the three; answer "—" rather than repeat. */
    offline: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Cell("SOURCE", if (offline) "—" else cells.source)
        Cell("LINK", if (offline) "—" else cells.link)
        Cell(
            "VERDICT",
            if (offline) "Unknown" else cells.verdict,
            accent = accentVerdict && !offline,
        )
    }
}

@Composable
private fun Cell(label: String, value: String, accent: Boolean = false) {
    Column {
        Text(
            text = label,
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.3.sp),
            maxLines = 1,
        )
        Text(
            text = value,
            color = if (accent) PlinthColors.Accent else PlinthColors.Muted,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
