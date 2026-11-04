package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.data.model.QueueItem
import dev.andrew.plinth.ui.components.Artwork
import dev.andrew.plinth.ui.components.LevelMeter
import dev.andrew.plinth.ui.components.ProgressHairline
import dev.andrew.plinth.ui.components.QueueBand
import dev.andrew.plinth.ui.components.SignalStrip
import dev.andrew.plinth.ui.components.Transport
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.theme.PlinthColors
import dev.andrew.plinth.ui.vm.PlayerUi
import kotlin.math.roundToInt

private val COVER = 420.dp
private val COLUMN = 584.dp
private val BAND = 152.dp
private val INSET = 56.dp

/**
 * The tablet's now-playing surface: a 420 dp cover, a 584 dp column whose bottom is
 * pinned to the cover's, and a 152 dp band on the window's bottom edge. The column is
 * a fixed height with its content bottom-aligned, so a two-line title grows upward and
 * the transport row never moves.
 */
@Composable
fun NowPlayingScreen(
    size: SurfaceSize,
    ui: PlayerUi,
    queue: List<QueueItem> = emptyList(),
    art: ArtRepository? = null,
    onPlayPause: () -> Unit = {},
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Double) -> Unit = {},
    onQueueItem: (Int) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(PlinthColors.Base)) {
        if (size == SurfaceSize.Medium) {
            // 600–839 dp: the cover would squeeze the column, so the spine stacks instead
            MediumNowPlaying(ui, queue, art, onPlayPause, onPrev, onNext, onSeek, onQueueItem, onOpenQueue)
        } else if (size == SurfaceSize.Expanded) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = INSET, end = INSET, top = INSET, bottom = BAND + 16.dp),
            ) {
                Row {
                    // the tablet has no chrome, so the cover is the library's door
                    Artwork(
                        albumId = ui.albumId,
                        firstTrackUri = ui.uri,
                        art = art,
                        modifier = Modifier.size(COVER).clickable { onOpenLibrary() },
                    )
                    Spacer(Modifier.width(40.dp))
                    TrackColumn(ui, size, onPlayPause, onPrev, onNext, onSeek)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BAND),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QueueBand(
                    items = queue,
                    onItemClick = onQueueItem,
                    onOpenQueue = onOpenQueue,
                    modifier = Modifier.weight(1f),
                )
                LevelMeter(
                    modifier = Modifier
                        .width(220.dp)
                        .padding(end = INSET),
                    enabled = ui.connected,
                )
            }
        } else {
            CompactNowPlaying(ui, art, onPlayPause, onPrev, onNext, onSeek)
        }
    }
}

@Composable
private fun TrackColumn(
    ui: PlayerUi,
    size: SurfaceSize,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
) {
    Column(
        modifier = Modifier.width(COLUMN).height(COVER),
        verticalArrangement = Arrangement.Bottom,
    ) {
        val title = ui.title.ifBlank { "Nothing playing" }
        val size = titleSizeFor(title, COLUMN)
        Text(
            text = title,
            color = PlinthColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = size,
                lineHeight = size * 1.08f,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = ui.artist,
            color = PlinthColors.Muted,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = position(ui.elapsed, ui.duration),
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        ui.strip?.let { SignalStrip(it) }
        Spacer(Modifier.height(24.dp))
        ProgressHairline(ui.elapsed, ui.duration, onSeek)
        Spacer(Modifier.height(16.dp))
        Transport(
            isPlaying = ui.isPlaying,
            enabled = ui.connected,
            onPrev = onPrev,
            onPlayPause = onPlayPause,
            onNext = onNext,
        )
    }
}

@Composable
private fun CompactNowPlaying(
    ui: PlayerUi,
    art: ArtRepository?,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Artwork(
            albumId = ui.albumId,
            firstTrackUri = ui.uri,
            art = art,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = ui.title.ifBlank { "Nothing playing" },
            color = PlinthColors.Text,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(ui.artist, color = PlinthColors.Muted, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        ui.strip?.let { SignalStrip(it) }
        Spacer(Modifier.height(16.dp))
        ProgressHairline(ui.elapsed, ui.duration, onSeek)
        Spacer(Modifier.height(8.dp))
        Transport(ui.isPlaying, ui.connected, onPrev, onPlayPause, onNext)
    }
}

/**
 * 64 sp while the title still fits one line at the column's width; 44 sp the moment
 * it would wrap. Measured, not counted: "Intro (Nine Inch Noize Version)" is short
 * in characters and still too wide at 64 sp.
 */
@Composable
private fun titleSizeFor(title: String, width: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxPx = with(density) { width.toPx() }.toInt()
    val big = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 69.sp)
    val singleLine = remember(title, maxPx) {
        runCatching {
            measurer.measure(
                text = title,
                style = big,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxPx),
            ).lineCount == 1
        }.getOrDefault(false)
    }
    return if (singleLine) 64.sp else 44.sp
}

private fun position(elapsed: Double?, duration: Double?): String {
    if (elapsed == null) return "—"
    return "${clock(elapsed)} / ${duration?.let { clock(it) } ?: "—"}"
}

private fun clock(seconds: Double): String {
    val s = seconds.roundToInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

