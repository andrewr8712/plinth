package dev.andrew.plinth.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.sp
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
) {
    var bitmap by remember(albumId) { mutableStateOf<ImageBitmap?>(null) }

    // `art` is in the key on purpose: it arrives once the connection is up, and a
    // key that ignored it would leave the cover blank forever on a cold start.
    LaunchedEffect(albumId, firstTrackUri, art) {
        bitmap = null
        if (art == null || albumId.isEmpty() || firstTrackUri.isEmpty()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            when (val result = art.forAlbum(albumId, firstTrackUri)) {
                is ArtResult.Jpeg -> runCatching {
                    BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)?.asImageBitmap()
                }.getOrNull()
                else -> null
            }
        }
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
            Text(
                text = "—",
                color = PlinthColors.Faint,
                fontSize = 28.sp,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
