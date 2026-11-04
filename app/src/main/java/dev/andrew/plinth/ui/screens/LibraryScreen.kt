package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.model.Album
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.theme.PlinthColors


/**
 * The library, as covers. Sort is by artist then year — the way a shelf is ordered —
 * and the edition chip marks a surround mix so it is never mistaken for the album.
 */
@Composable
fun LibraryScreen(
    size: SurfaceSize,
    albums: List<Album>,
    syncing: Boolean = false,
    onOpenAlbum: (String) -> Unit = {},
) {

    val columns = when (size) {
        SurfaceSize.Compact -> 2
        SurfaceSize.Medium -> 3
        SurfaceSize.Expanded -> 5
    }

    Column(Modifier.fillMaxSize().background(PlinthColors.Base)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Library",
                color = PlinthColors.Text,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (syncing) "Syncing…" else "${albums.size} albums",
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (syncing) "Reading the library…" else "No albums cached yet",
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().testTag("albumGrid"),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumTile(album, onClick = { onOpenAlbum(album.id) })
            }
        }
    }
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(PlinthColors.Plate),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = album.title.take(1).uppercase(),
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Text(
            text = album.title,
            color = PlinthColors.Text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = listOfNotNull(album.artist, album.edition).joinToString(" · "),
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
