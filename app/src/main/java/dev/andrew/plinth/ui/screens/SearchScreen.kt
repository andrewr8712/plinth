package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.model.Album
import dev.andrew.plinth.data.model.Track
import dev.andrew.plinth.ui.components.ListRow
import dev.andrew.plinth.ui.components.TrackRow
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * Search as you type. Room answers the track query (prefix on title, album, artist) and
 * albums are filtered from the cached list, so results appear without a round trip.
 */
@Composable
fun SearchScreen(
    query: String,
    albums: List<Album>,
    tracks: List<Track>,
    onQueryChange: (String) -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(PlinthColors.Base)) {
        Text(
            text = "Search",
            color = PlinthColors.Text,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
        )

        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Title, album or artist", color = PlinthColors.Faint) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PlinthColors.Plate,
                unfocusedContainerColor = PlinthColors.Plate,
                focusedTextColor = PlinthColors.Text,
                unfocusedTextColor = PlinthColors.Text,
                cursorColor = PlinthColors.Accent,
                focusedIndicatorColor = PlinthColors.Accent,
                unfocusedIndicatorColor = PlinthColors.Hair,
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("search-field"),
        )

        if (query.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Type to search",
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }

        if (albums.isEmpty() && tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing matches “$query”",
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp).testTag("searchResults")) {
            if (albums.isNotEmpty()) {
                item { SectionLabel("Albums") }
                items(albums, key = { "album-${it.id}" }) { album ->
                    ListRow(
                        text = album.title.ifBlank { album.firstUri.substringAfterLast('/') },
                        secondary = listOf(album.artist, "${album.trackCount} tracks")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = { onOpenAlbum(album.id) },
                        testTag = "search-album-row",
                    )
                }
            }
            if (tracks.isNotEmpty()) {
                item { SectionLabel("Tracks") }
                items(tracks, key = { it.uri }) { track ->
                    TrackRow(
                        title = track.title,
                        subtitle = listOf(track.artist.ifBlank { track.albumArtist }, track.album)
                            .filter { it.isNotBlank() }.distinct().joinToString(" · "),
                        durationSec = track.durationSec,
                        onClick = { onPlayTrack(track) },
                        testTag = "search-track-row",
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = PlinthColors.Faint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** Album matches come from the cached list, not the database, so they are pure. */
fun albumsMatching(albums: List<Album>, query: String): List<Album> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return albums.filter {
        it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
    }
}
