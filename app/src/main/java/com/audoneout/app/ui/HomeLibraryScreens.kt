package com.audoneout.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audoneout.app.LibrarySort
import com.audoneout.app.LibraryView
import com.audoneout.app.MainUiState
import com.audoneout.app.data.AlbumEntity
import com.audoneout.app.data.ArtistEntity
import com.audoneout.app.data.FolderEntity
import com.audoneout.app.data.PlaylistEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    state: MainUiState,
    trackCallbacks: TrackCallbacks,
    onSetUpLibrary: () -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenDoctor: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenDiscover: () -> Unit
) {
    val duplicateStorageBytes = remember(state.tracks, state.analysisResults) {
        duplicateStorageEstimate(state)
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Your music, ready", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${state.library.trackCount} local tracks", color = AudColors.TextSecondary)
            }
        }
        if (!state.hasLibrary) {
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = AudColors.Surface)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Storage, null, tint = AudColors.Teal, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Set up your library", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Choose the folders AudOneOut may index.", color = AudColors.TextSecondary)
                            }
                        }
                        Button(onClick = onSetUpLibrary, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose music folders")
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                        OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
                            Icon(Icons.Rounded.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan MediaStore audio")
                        }
                    }
                }
            }
        }
        if (state.scanRunning) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = AudColors.Teal)
                    Text(
                        listOf(state.scanProgress.currentRoot, state.scanProgress.currentFolder).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Working..." },
                        color = AudColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Found ${state.scanProgress.tracksFound} - New ${state.scanProgress.newTracks} - Updated ${state.scanProgress.updatedTracks} - Missing ${state.scanProgress.missingTracks} - Excluded ${state.scanProgress.excludedTracks} - Failed ${state.scanProgress.failedTracks}",
                        color = AudColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    OutlinedButton(onClick = onCancelScan, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Cancel, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel scan")
                    }
                }
            }
        }
        if (state.hasLibrary) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Metric("Tracks", state.library.trackCount.toString(), AudColors.Coral, Modifier.weight(1f))
                    Metric("Albums", state.library.albumCount.toString(), AudColors.Teal, Modifier.weight(1f))
                    Metric("Health", "${state.health.score}%", AudColors.Amber, Modifier.weight(1f))
                }
            }
            item {
                SectionHeading("Speed Dial")
            }
            if (state.favorites.isEmpty()) {
                item {
                    EmptyPanel(
                        icon = { Icon(Icons.Rounded.Favorite, null, tint = AudColors.Coral, modifier = Modifier.size(32.dp)) },
                        title = "No songs pinned",
                        body = "Use the heart beside a song to add it here."
                    )
                }
            } else {
                items(state.favorites.take(12).chunked(3)) { rowTracks ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowTracks.forEach { track ->
                            SpeedDialTile(track, trackCallbacks.onOpen, Modifier.weight(1f))
                        }
                        repeat(3 - rowTracks.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item {
                SectionHeading("Library pulse")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeAction(
                        title = "Check library",
                        value = state.lastCompletedScan?.finishedAtMillis?.let {
                            "Last ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}"
                        } ?: state.scanProgress.estimatedRemainingWork,
                        icon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = onScan,
                        modifier = Modifier.weight(1f)
                    )
                    HomeAction(
                        title = "Library Doctor",
                        value = if (duplicateStorageBytes > 0) {
                            "${state.analysisResults.size} findings, ${formatBytes(duplicateStorageBytes)} duplicate"
                        } else {
                            "${state.analysisResults.size} findings"
                        },
                        icon = { Icon(Icons.Rounded.HealthAndSafety, null) },
                        onClick = onOpenDoctor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeAction(
                        title = "New Music",
                        value = "${state.inbox.size} to review",
                        icon = { Icon(Icons.Rounded.LibraryMusic, null) },
                        onClick = onOpenInbox,
                        modifier = Modifier.weight(1f)
                    )
                    HomeAction(
                        title = "For You",
                        value = "${state.recommendations.size} local picks",
                        icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                        onClick = onOpenDiscover,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item { SectionHeading("Recently added") }
            items(state.tracks.sortedByDescending { it.dateAddedSeconds }.take(8), key = { "recent-${it.id}" }) { track ->
                TrackRow(track, track.id in state.favoriteIds, state.playlists, trackCallbacks)
            }
        }
    }
}

private fun duplicateStorageEstimate(state: MainUiState): Long {
    val duplicateIds = state.analysisResults
        .filter { it.issueType == "Possible duplicate" }
        .mapTo(mutableSetOf()) { it.trackId }
    return state.tracks
        .filter { it.id in duplicateIds }
        .groupBy {
            listOf(
                it.title.normalizedDuplicateKey(),
                it.artist.normalizedDuplicateKey(),
                it.album.normalizedDuplicateKey(),
                (it.durationMs / 1000).toString(),
                it.sizeBytes.toString(),
                it.format.lowercase()
            ).joinToString("|")
        }
        .values
        .sumOf { group -> group.drop(1).sumOf { it.sizeBytes } }
}

private fun String.normalizedDuplicateKey(): String =
    lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()

@Composable
private fun HomeAction(
    title: String,
    value: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = AudColors.Surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = AudColors.SurfaceRaised, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(36.dp)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { icon() }
            }
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LibraryScreen(
    state: MainUiState,
    trackCallbacks: TrackCallbacks,
    onViewChange: (LibraryView) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleGrid: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onImportPlaylist: () -> Unit
) {
    var sortMenu by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.libraryQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search library", maxLines = 1) }
            )
        }
        item {
            ScrollableTabRow(
                selectedTabIndex = LibraryView.values().indexOf(state.libraryView),
                edgePadding = 12.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                LibraryView.values().forEach { view ->
                    Tab(
                        selected = state.libraryView == view,
                        onClick = { onViewChange(view) },
                        text = { Text(view.name) }
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (state.libraryView) {
                        LibraryView.Songs -> "${state.tracks.size} songs"
                        LibraryView.Albums -> "${state.albums.size} albums"
                        LibraryView.Artists -> "${state.artists.size} artists"
                        LibraryView.Folders -> "${state.folders.size} folders"
                        LibraryView.Playlists -> "${state.playlists.size} playlists"
                    },
                    color = AudColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.foundation.layout.Box {
                    FilledTonalButton(onClick = { sortMenu = true }) {
                        Icon(Icons.Rounded.Sort, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(state.librarySort.label)
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        LibrarySort.values().forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = { sortMenu = false; onSortChange(sort) }
                            )
                        }
                    }
                }
                if (state.libraryView == LibraryView.Songs || state.libraryView == LibraryView.Albums) {
                    IconButton(onClick = onToggleGrid) {
                        Icon(if (state.libraryGridMode) Icons.Rounded.List else Icons.Rounded.GridView, if (state.libraryGridMode) "List view" else "Grid view")
                    }
                }
            }
        }
        when (state.libraryView) {
            LibraryView.Songs -> {
                if (state.tracks.isEmpty()) item { LibraryEmpty("No songs found", Icons.Rounded.MusicNote) }
                else if (state.libraryGridMode) {
                    items(state.tracks.chunked(2)) { tracks ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            tracks.forEach { track -> SpeedDialTile(track, trackCallbacks.onOpen, Modifier.weight(1f)) }
                            if (tracks.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    items(state.tracks, key = { "track-${it.id}" }) { track ->
                        TrackRow(track, track.id in state.favoriteIds, state.playlists, trackCallbacks)
                    }
                }
            }
            LibraryView.Albums -> {
                if (state.albums.isEmpty()) item { LibraryEmpty("No albums found", Icons.Rounded.Album) }
                else if (state.libraryGridMode) {
                    items(state.albums.chunked(2)) { albums ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            albums.forEach { album ->
                                AlbumGridTile(album, onOpenAlbum, Modifier.weight(1f))
                            }
                            if (albums.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    items(state.albums, key = { it.albumKey }) { album -> AlbumRow(album, onOpenAlbum) }
                }
            }
            LibraryView.Artists -> {
                if (state.artists.isEmpty()) item { LibraryEmpty("No artists found", Icons.Rounded.Person) }
                else items(state.artists, key = { it.artistKey }) { artist -> ArtistRow(artist, onOpenArtist) }
            }
            LibraryView.Folders -> {
                if (state.folders.isEmpty()) item { LibraryEmpty("No folders found", Icons.Rounded.Folder) }
                else items(state.folders, key = { it.path }) { folder -> FolderRow(folder, onOpenFolder) }
            }
            LibraryView.Playlists -> {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = playlistName,
                                onValueChange = { playlistName = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("New playlist name") }
                            )
                            IconButton(
                                onClick = { onCreatePlaylist(playlistName); playlistName = "" },
                                enabled = playlistName.isNotBlank()
                            ) { Icon(Icons.Rounded.Add, "Create playlist") }
                        }
                        OutlinedButton(onClick = onImportPlaylist, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.LibraryMusic, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import M3U, M3U8, or CSV")
                        }
                    }
                }
                if (state.playlists.isEmpty()) item { LibraryEmpty("No playlists yet", Icons.Rounded.LibraryMusic) }
                else items(state.playlists, key = { it.id }) { playlist -> PlaylistRow(playlist, onOpenPlaylist) }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumEntity, onOpen: (String) -> Unit) {
    CollectionRow(
        artwork = album.artworkUri,
        title = album.title,
        subtitle = "${album.artistName} - ${album.trackCount} songs",
        icon = { Icon(Icons.Rounded.Album, null) },
        onClick = { onOpen(album.albumKey) }
    )
}

@Composable
private fun AlbumGridTile(
    album: AlbumEntity,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable { onOpen(album.albumKey) },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Artwork(album.artworkUri, album.title, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(
            "${album.artistName} - ${album.trackCount} songs",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = AudColors.TextMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ArtistRow(artist: ArtistEntity, onOpen: (String) -> Unit) {
    CollectionRow(
        artwork = null,
        title = artist.name,
        subtitle = "${artist.trackCount} songs - ${artist.albumCount} albums",
        icon = { Icon(Icons.Rounded.Person, null) },
        onClick = { onOpen(artist.artistKey) }
    )
}

@Composable
private fun FolderRow(folder: FolderEntity, onOpen: (String) -> Unit) {
    CollectionRow(
        artwork = null,
        title = folder.name,
        subtitle = "${folder.trackCount} songs - ${formatBytes(folder.storageBytes)}",
        icon = { Icon(Icons.Rounded.Folder, null) },
        onClick = { onOpen(folder.path) }
    )
}

@Composable
private fun PlaylistRow(playlist: PlaylistEntity, onOpen: (Long) -> Unit) {
    CollectionRow(
        artwork = null,
        title = playlist.name,
        subtitle = playlist.description.ifBlank { "Manual playlist" },
        icon = { Icon(Icons.Rounded.LibraryMusic, null) },
        onClick = { onOpen(playlist.id) },
        opensExternally = false
    )
}

@Composable
private fun CollectionRow(
    artwork: String?,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    opensExternally: Boolean = true
) {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (artwork != null) Artwork(artwork, title, Modifier.size(54.dp))
            else Surface(color = AudColors.SurfaceRaised, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(54.dp)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { icon() }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                if (opensExternally) Icons.Rounded.OpenInNew else Icons.Rounded.ChevronRight,
                if (opensExternally) "Open in music player" else "Open playlist details",
                tint = AudColors.TextSecondary
            )
        }
    }
}

@Composable
private fun LibraryEmpty(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EmptyPanel(
        icon = { Icon(icon, null, tint = AudColors.TextMuted, modifier = Modifier.size(34.dp)) },
        title = title,
        body = "Try another search or scan your music folders."
    )
}
