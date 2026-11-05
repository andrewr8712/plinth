package dev.andrew.plinth.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.data.art.ArtResult
import dev.andrew.plinth.ui.theme.PlinthColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The cover, or an honest empty plate. Art arrives once per album and is cached on disk. */
@Composable
fun Artwork(
    albumId: String,
    firstTrackUri: String,
    art: ArtRepository?,
    modifier: Modifier = Modifier,
    /** Album title, only used to draw initials when there is no cover. */
    label: String = "",
) {
    var bitmap by remember(albumId) { mutableStateOf<ImageBitmap?>(null) }

    // `missing` is a fact from the server (ACK 50), not the absence of a fetch yet: the
    // plate says "No artwork" only when the album really has none.
    var missing by remember(albumId) { mutableStateOf(false) }

    // The rendered size in pixels. The cover is decoded for the size it will be drawn
    // at, not its source size: the library has 1600² and 3700² covers, and the grid
    // draws nine of them at once.
    var targetPx by remember { mutableStateOf(0) }

    // `art` is in the key on purpose: it arrives once the connection is up, and a
    // key that ignored it would leave the cover blank forever on a cold start.
    LaunchedEffect(albumId, firstTrackUri, art, targetPx) {
        bitmap = null
        missing = false
        if (art == null || albumId.isEmpty() || firstTrackUri.isEmpty() || targetPx <= 0) {
            return@LaunchedEffect
        }
        // Decode and decide off the main thread, then publish both facts in one go:
        // a snapshot-state write from the IO thread would be a change nobody observed.
        val loaded = withContext(Dispatchers.IO) {
            when (val result = art.forAlbum(albumId, firstTrackUri)) {
                is ArtResult.Jpeg -> runCatching {
                    decodeSampled(result.bytes, targetPx)?.asImageBitmap()
                }.getOrNull() to false
                ArtResult.None -> null to true
                else -> null to false
            }
        }
        bitmap = loaded.first
        missing = loaded.second
    }

    Box(
        modifier = modifier
            .onSizeChanged { targetPx = maxOf(it.width, it.height) }
            .background(PlinthColors.Plate)
            .testTag("cover"),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            ArtworkPlate(label = label, noArtwork = missing)
        }
    }
}

/**
 * Initials on the plate instead of a broken-image icon or a spinner. The wash is
 * dropped rather than tinted grey, because a grey cover looks like a bug.
 */
@Composable
fun ArtworkPlate(
    label: String,
    noArtwork: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().testTag("artwork-plate"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = initials(label),
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.displayMedium,
            )
            if (noArtwork) {
                Text(
                    text = "No artwork",
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

internal fun initials(label: String): String {
    val words = label.trim().split(' ', '\t', '\n').filter { it.isNotBlank() }
    val letters = words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
    return letters.joinToString("").ifEmpty { "—" }
}

/**
 * Decode a JPEG to at least [targetPx] on its shorter side, using a power-of-two
 * `inSampleSize`. The source covers range from 1600² to 3700²; at full size the
 * 3700² one is ~55 MB of ARGB, which is more than the grid or the sheet needs.
 */
private fun decodeSampled(bytes: ByteArray, targetPx: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx &&
        bounds.outHeight / (sample * 2) >= targetPx
    ) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}
