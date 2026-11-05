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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.art.ArtRepository
import dev.andrew.plinth.data.model.Album
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.ui.components.Artwork
import dev.andrew.plinth.ui.components.EmptyState
import dev.andrew.plinth.ui.components.ListRow
import dev.andrew.plinth.ui.components.TrackRow
import dev.andrew.plinth.ui.components.UnavailableChip
import dev.andrew.plinth.ui.layout.SurfaceSize
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * The four ways into the same library. Albums are the shelf; artists and folders are
 * how this collection is actually laid out on disk (no reliable artist folders, some
 * albums with no AlbumArtist at all), so they are peers rather than drill-downs.
 */
enum class LibraryTab(val label: String) {
    Albums("Albums"),
    Artists("Artists"),
    Tracks("Tracks"),
    Folders("Folders"),
}

@Composable
fun LibraryScreen(
    size: SurfaceSize,
    tab: LibraryTab,
    onTab: (LibraryTab) -> Unit,
    albums: List<Album>,
    artists: List<String>,
    tracks: List<Track>,
    folders: List<String>,
    art: ArtRepository? = null,
    syncing: Boolean = false,
    offline: Boolean = false,
    offlineReason: String = "",
    onRetry: () -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
) {
    val count = when (tab) {
        LibraryTab.Albums -> albums.size
        LibraryTab.Artists -> artists.size
        LibraryTab.Tracks -> tracks.size
        LibraryTab.Folders -> folders.size
    }
    val noun = when (tab) {
        LibraryTab.Albums -> "albums"
        LibraryTab.Artists -> "artists"
        LibraryTab.Tracks -> "tracks"
        LibraryTab.Folders -> "folders"
    }

    Column(Modifier.fillMaxSize().background(PlinthColors.Base)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Library",
                color = PlinthColors.Text,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (syncing) "Syncing…" else "$count $noun",
                color = PlinthColors.Faint,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LibraryTabs(tab, onTab)

        if (offline) {
            UnavailableChip(
                reason = offlineReason.ifBlank { "Streamer not answering" },
                onRetry = onRetry,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
        }

        if (count == 0) {
            if (syncing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Reading the library…",
                        color = PlinthColors.Faint,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                EmptyState(
                    title = "Library is empty",
                    body = "The streamer can see MPD but not its music share. " +
                        "Its SMB mount is probably down.",
                    actionLabel = "Retry",
                    onAction = onRetry,
                    testTag = "library-empty",
                )
            }
            return@Column
        }

        when (tab) {
            LibraryTab.Albums -> AlbumGrid(size, albums, art, onOpenAlbum)
            LibraryTab.Artists -> LazyColumn(Modifier.fillMaxSize().testTag("artistList")) {
                items(artists, key = { it }) { artist ->
                    ListRow(text = artist, onClick = { onOpenArtist(artist) }, testTag = "artist-row")
                }
            }
            LibraryTab.Tracks -> LazyColumn(Modifier.fillMaxSize().testTag("allTracks")) {
                items(tracks, key = { it.uri }) { track ->
                    TrackRow(
                        title = track.title,
                        subtitle = listOf(track.artist.ifBlank { track.albumArtist }, track.album)
                            .filter { it.isNotBlank() }.distinct().joinToString(" · "),
                        durationSec = track.durationSec,
                        onClick = { onPlayTrack(track) },
                        testTag = "all-track-row",
                    )
                }
            }
            LibraryTab.Folders -> LazyColumn(Modifier.fillMaxSize().testTag("folderList")) {
                items(folders, key = { it }) { folder ->
                    ListRow(text = folder, onClick = { onOpenFolder(folder) }, testTag = "folder-row")
                }
            }
        }
    }
}

@Composable
private fun LibraryTabs(tab: LibraryTab, onTab: (LibraryTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        LibraryTab.entries.forEach { entry ->
            val selected = entry == tab
            // IntrinsicSize.Max keeps each tab as wide as its label: a plain
            // fillMaxWidth underline would let the first tab take the whole row and
            // leave the other three zero-width.
            Column(
                modifier = Modifier
                    .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                    .clickable { onTab(entry) }
                    .testTag("tab-${entry.label}"),
            ) {
                Text(
                    text = entry.label,
                    color = if (selected) PlinthColors.Text else PlinthColors.Faint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Box(
                    Modifier
                        .height(2.dp)
                        .fillMaxWidth()
                        .background(if (selected) PlinthColors.Accent else PlinthColors.Base),
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    size: SurfaceSize,
    albums: List<Album>,
    art: ArtRepository?,
    onOpenAlbum: (String) -> Unit,
) {
    val columns = when (size) {
        SurfaceSize.Compact -> 2
        SurfaceSize.Medium -> 3
        SurfaceSize.Expanded -> 5
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().testTag("albumGrid"),
    ) {
        items(albums.size, key = { albums[it].id }) { index ->
            val album = albums[index]
            AlbumTile(album, art, onClick = { onOpenAlbum(album.id) })
        }
    }
}

@Composable
private fun AlbumTile(album: Album, art: ArtRepository?, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.testTag("album-tile")) {
        Artwork(
            albumId = album.id,
            firstTrackUri = album.firstUri,
            art = art,
            label = album.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            text = album.title,
            color = PlinthColors.Text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = listOfNotNull(album.artist.ifBlank { null }, album.edition).joinToString(" · "),
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
