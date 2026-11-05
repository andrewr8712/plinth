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

    // `art` is in the key on purpose: it arrives once the connection is up, and a
    // key that ignored it would leave the cover blank forever on a cold start.
    LaunchedEffect(albumId, firstTrackUri, art) {
        bitmap = null
        missing = false
        if (art == null || albumId.isEmpty() || firstTrackUri.isEmpty()) return@LaunchedEffect
        // Decode and decide off the main thread, then publish both facts in one go:
        // a snapshot-state write from the IO thread would be a change nobody observed.
        val loaded = withContext(Dispatchers.IO) {
            when (val result = art.forAlbum(albumId, firstTrackUri)) {
                is ArtResult.Jpeg -> runCatching {
                    BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)?.asImageBitmap()
                }.getOrNull() to false
                ArtResult.None -> null to true
                else -> null to false
            }
        }
        bitmap = loaded.first
        missing = loaded.second
    }

    Box(
        modifier = modifier.background(PlinthColors.Plate).testTag("cover"),
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
