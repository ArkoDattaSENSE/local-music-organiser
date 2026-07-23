package com.audoneout.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audoneout.app.MainUiState
import com.audoneout.app.data.AnalysisResultEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.data.UserConfirmedMetadataEntity
import com.audoneout.app.metadata.MetadataWritebackSupport
import com.audoneout.app.metadata.TrackMetadataInput
import java.text.DateFormat
import java.util.Date

@Composable
fun InboxScreen(
    state: MainUiState,
    callbacks: TrackCallbacks,
    onRunDoctor: () -> Unit,
    onAcceptSafeSuggestions: () -> Unit,
    onOpenDoctor: () -> Unit
) {
    val safeSuggestionCount = state.metadataSuggestions.count {
        it.confidence >= 0.8f && it.field in setOf("title", "artist", "album")
    }
    val pendingWritebackCount = state.metadataWritebacks.count {
        it.status in setOf("Pending", "Partial", "Failed")
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Review", state.inbox.size.toString(), AudColors.Amber, Modifier.weight(1f))
                Metric("Writeback", pendingWritebackCount.toString(), AudColors.Coral, Modifier.weight(1f))
                Metric("Excluded", state.inbox.count { it.enhancementStatus == "Excluded" }.toString(), AudColors.Teal, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onRunDoctor, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Analyse")
                }
                OutlinedButton(onClick = onOpenDoctor, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.HealthAndSafety, null)
                    Spacer(Modifier.width(6.dp))
                    Text("All findings")
                }
            }
        }
        if (safeSuggestionCount > 0) {
            item {
                FilledTonalButton(
                    onClick = onAcceptSafeSuggestions,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CheckCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Accept $safeSuggestionCount high-confidence suggestions")
                }
            }
        }
        if (state.inbox.isEmpty()) {
            item {
                EmptyPanel(
                    icon = { Icon(Icons.Rounded.CheckCircle, null, tint = AudColors.Teal, modifier = Modifier.size(36.dp)) },
                    title = "Inbox is clear",
                    body = "Tracks needing metadata or duplicate review appear here."
                )
            }
        } else {
            items(state.inbox, key = { "inbox-${it.id}" }) { track ->
                val writebackStatus = state.writebackFor(track.id)?.status
                val hasSuggestions = state.metadataSuggestions.any { it.trackId == track.id }
                TrackRow(
                    track = track,
                    isFavorite = track.id in state.favoriteIds,
                    playlists = state.playlists,
                    callbacks = callbacks,
                    supportingText = inboxStatusLabel(track.enhancementStatus, writebackStatus, hasSuggestions),
                    onRowClick = callbacks.onDetails
                )
            }
        }
    }
}

@Composable
fun DoctorScreen(
    state: MainUiState,
    onRunDoctor: () -> Unit,
    onOpenTrack: (Long) -> Unit,
    onIgnore: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.HealthAndSafety, null, tint = AudColors.Teal, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Library health ${state.health.score}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("${state.health.missingMetadata} metadata issues - ${state.health.duplicateCandidates} duplicate candidates", color = AudColors.TextSecondary)
                        }
                    }
                    Button(onClick = onRunDoctor, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run Library Doctor")
                    }
                }
            }
        }
        if (state.analysisResults.isEmpty()) {
            item {
                EmptyPanel(
                    icon = { Icon(Icons.Rounded.CheckCircle, null, tint = AudColors.Teal, modifier = Modifier.size(36.dp)) },
                    title = "No active findings",
                    body = "Run Library Doctor after adding or changing music."
                )
            }
        } else {
            items(state.analysisResults, key = { it.id }) { result ->
                FindingRow(
                    result,
                    (state.tracks + state.inbox).firstOrNull { it.id == result.trackId },
                    onOpenTrack,
                    onIgnore
                )
            }
        }
    }
}

@Composable
private fun FindingRow(
    result: AnalysisResultEntity,
    track: TrackEntity?,
    onOpenTrack: (Long) -> Unit,
    onIgnore: (Long) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.severity.equals("info", true)) Icons.Rounded.Info else Icons.Rounded.Warning,
                    null,
                    tint = if (result.severity.equals("info", true)) AudColors.Teal else AudColors.Amber
                )
                Spacer(Modifier.width(8.dp))
                Text(result.issueType, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            if (track != null) Text("${track.artist} - ${track.title}", color = AudColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result.explanation, color = AudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onOpenTrack(result.trackId) }) { Text("Review") }
                OutlinedButton(onClick = { onIgnore(result.id) }) {
                    Icon(Icons.Rounded.Block, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Ignore")
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    state: MainUiState,
    callbacks: TrackCallbacks,
    onLoad: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onStartRadio: (Long) -> Unit,
    onExportM3u: (Long) -> Unit,
    onExportCsv: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRemoveTrack: (Long, Long) -> Unit,
    onMoveTrack: (Long, Long, Int) -> Unit
) {
    LaunchedEffect(playlistId) { onLoad(playlistId) }
    val playlist = state.playlists.firstOrNull { it.id == playlistId }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(playlist?.name ?: "Playlist", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!playlist?.description.isNullOrBlank()) Text(playlist?.description.orEmpty(), color = AudColors.TextSecondary)
            Text("${state.selectedPlaylistTracks.size} songs", color = AudColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            if (state.selectedPlaylistUnavailableCount > 0) {
                Text(
                    "${state.selectedPlaylistUnavailableCount} missing or excluded entries are omitted from handoff and export",
                    color = AudColors.Amber,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onOpen(playlistId) }, enabled = state.selectedPlaylistTracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.OpenInNew, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Open in player")
                }
                FilledTonalButton(onClick = { onStartRadio(playlistId) }, enabled = state.selectedPlaylistTracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Radio, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Radio")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onExportM3u(playlistId) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileDownload, null)
                    Spacer(Modifier.width(5.dp))
                    Text("M3U8")
                }
                OutlinedButton(onClick = { onExportCsv(playlistId) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileDownload, null)
                    Spacer(Modifier.width(5.dp))
                    Text("CSV")
                }
                IconButton(onClick = { onDelete(playlistId) }) { Icon(Icons.Rounded.DeleteOutline, "Delete playlist") }
            }
        }
        if (state.selectedPlaylistTracks.isEmpty()) {
            item {
                EmptyPanel(
                    icon = { Icon(Icons.Rounded.MusicNote, null, tint = AudColors.TextMuted, modifier = Modifier.size(34.dp)) },
                    title = "This playlist is empty",
                    body = "Use a song's menu to add it here."
                )
            }
        } else {
            itemsIndexed(state.selectedPlaylistTracks, key = { _, track -> "playlist-track-${track.id}" }) { index, track ->
                Column {
                    TrackRow(track, track.id in state.favoriteIds, state.playlists, callbacks)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { onMoveTrack(playlistId, track.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                        }
                        IconButton(onClick = { onMoveTrack(playlistId, track.id, 1) }, enabled = index < state.selectedPlaylistTracks.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                        }
                        IconButton(onClick = { onRemoveTrack(playlistId, track.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, "Remove from playlist")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackDetailScreen(
    trackId: Long,
    state: MainUiState,
    onOpen: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onStartRadio: (Long) -> Unit,
    onLoadEnrichment: (Long) -> Unit,
    onFindMetadata: (Long) -> Unit,
    onAcceptSuggestion: (Long) -> Unit,
    onIgnoreSuggestion: (Long) -> Unit,
    onSaveMetadata: (TrackMetadataInput) -> Unit,
    onWriteMetadata: (Long) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(trackId) { onLoadEnrichment(trackId) }
    val track = (state.tracks + state.inbox).firstOrNull { it.id == trackId }
    if (track == null) {
        EmptyPanel(
            icon = { Icon(Icons.Rounded.Warning, null, tint = AudColors.Amber, modifier = Modifier.size(34.dp)) },
            title = "Track unavailable",
            body = "Rescan the library to refresh this file.",
            modifier = Modifier.padding(16.dp)
        )
        return
    }
    val findings = state.analysisResults.filter { it.trackId == trackId }
    val suggestions = state.metadataSuggestions.filter { it.trackId == trackId }
    val confirmedMetadata = state.confirmedMetadataFor(trackId)
    val writeback = state.writebackFor(trackId)
    val originalMetadata = state.selectedTrackOriginalMetadata
        .takeIf { state.selectedTrackEnrichmentId == trackId }
    val differsFromOriginal = originalMetadata?.let {
        it.title != track.title ||
            it.artist != track.artist ||
            it.album != track.album ||
            it.albumArtist != track.albumArtist ||
            it.genre != track.genre ||
            it.year != track.year
    } == true
    val discoveryInferences = state.selectedTrackEnrichment
        .takeIf { state.selectedTrackEnrichmentId == trackId }
        .orEmpty()
        .filter {
            it.key.lowercase() in setOf(
                "tags",
                "discoverytags",
                "era",
                "quality",
                "mood",
                "energy",
                "character",
                "language"
            )
        }
        .sortedByDescending { it.confidence }
    val writebackSupported = MetadataWritebackSupport.supports(track.fileName)
    val writebackRunning = state.metadataWritebackTrackId == trackId
    var showMetadataEditor by remember(trackId) { mutableStateOf(false) }
    var showWriteConfirmation by remember(trackId) { mutableStateOf(false) }
    if (showMetadataEditor) {
        MetadataEditorDialog(
            initial = TrackMetadataInput.from(track),
            onDismiss = { showMetadataEditor = false },
            onSave = {
                onSaveMetadata(it)
                showMetadataEditor = false
            }
        )
    }
    if (showWriteConfirmation && confirmedMetadata != null) {
        AlertDialog(
            onDismissRequest = { showWriteConfirmation = false },
            title = { Text("Write metadata to this file?") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("AudOneOut will write only these reviewed fields:")
                    Text(
                        confirmedMetadata.writebackPreviewLines().joinToString("\n"),
                        color = AudColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Existing artwork and unrelated tags are preserved. Android may ask you to approve this file.",
                        color = AudColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWriteConfirmation = false
                        onWriteMetadata(track.id)
                    }
                ) {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Write and verify")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWriteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.albumArtUri, track.title, Modifier.size(104.dp), cornerRadius = 8)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist.ifBlank { "Unknown artist" }, color = AudColors.TextSecondary)
                    Text(track.album.ifBlank { "Unknown album" }, color = AudColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onOpen(track.id) },
                    enabled = track.availability == "Available",
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.OpenInNew, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Open in player")
                }
                FilledTonalButton(
                    onClick = { onStartRadio(track.id) },
                    enabled = track.availability == "Available",
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Radio, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Radio")
                }
                IconButton(onClick = { onToggleFavorite(track.id) }) {
                    Icon(Icons.Rounded.Favorite, if (track.id in state.favoriteIds) "Remove from Speed Dial" else "Add to Speed Dial", tint = if (track.id in state.favoriteIds) AudColors.Coral else AudColors.TextSecondary)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AudOneOut metadata", fontWeight = FontWeight.Bold)
                    Text(
                        "Used for search, Library Doctor, mixtapes, radio, and recommendations",
                        color = AudColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    MetadataLine("Title", track.title)
                    MetadataLine("Artist", track.artist.ifBlank { "Unknown" })
                    MetadataLine("Album", track.album.ifBlank { "Unknown" })
                    MetadataLine("Album artist", track.albumArtist.ifBlank { "Unknown" })
                    MetadataLine("Genre", track.genre.ifBlank { "Not tagged" })
                    MetadataLine("Year", track.year?.toString() ?: "Not tagged")
                    MetadataLine("Language", track.language.ifBlank { "Not inferred" })
                    MetadataLine("Mood", track.mood.ifBlank { "Not inferred" })
                    MetadataLine("Energy", track.energy?.let { "$it / 100" } ?: "Not inferred")
                    MetadataLine("Discovery tags", track.discoveryTags.ifBlank { "None yet" })
                    MetadataLine("Format", track.format.ifBlank { track.mimeType })
                    MetadataLine("Duration", formatDuration(track.durationMs))
                    MetadataLine("Size", formatBytes(track.sizeBytes))
                    MetadataLine("Folder", track.relativePath)
                }
            }
        }
        if (differsFromOriginal && originalMetadata != null) {
            item {
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Original scan snapshot", fontWeight = FontWeight.Bold)
                        Text(
                            "Kept separately so accepted corrections never erase the starting metadata",
                            color = AudColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        MetadataLine("Title", originalMetadata.title)
                        MetadataLine("Artist", originalMetadata.artist.ifBlank { "Unknown" })
                        MetadataLine("Album", originalMetadata.album.ifBlank { "Unknown" })
                        MetadataLine("Album artist", originalMetadata.albumArtist.ifBlank { "Unknown" })
                        MetadataLine("Genre", originalMetadata.genre.ifBlank { "Not tagged" })
                        MetadataLine("Year", originalMetadata.year?.toString() ?: "Not tagged")
                    }
                }
            }
        }
        if (discoveryInferences.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Discovery signal provenance", fontWeight = FontWeight.Bold)
                        discoveryInferences.forEach { value ->
                            Column {
                                Text(
                                    inferenceLabel(value.key),
                                    color = AudColors.TextMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(value.value, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    value.source,
                                    color = AudColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                val reviewLabel = if (value.confirmed) {
                                    "User confirmed"
                                } else {
                                    "${(value.confidence * 100).toInt()}% confidence"
                                }
                                Text(
                                    "$reviewLabel - ${
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(value.createdAtMillis))
                                    }",
                                    color = AudColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoFixHigh, null, tint = AudColors.Teal)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Metadata assistant", fontWeight = FontWeight.Bold)
                            Text(
                                "Local inferences shape discovery; online corrections and file tags always require review",
                                color = AudColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showMetadataEditor = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Edit, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Edit metadata")
                    }
                    Button(
                        onClick = { onFindMetadata(track.id) },
                        enabled = state.metadataLookupTrackId == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Find metadata with Last.fm")
                    }
                    if (state.metadataLookupTrackId == track.id) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Save, null, tint = AudColors.Coral)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("File writeback", fontWeight = FontWeight.Bold)
                            Text(
                                writebackLabel(
                                    hasConfirmedMetadata = confirmedMetadata != null,
                                    supported = writebackSupported,
                                    running = writebackRunning,
                                    status = writeback?.status
                                ),
                                color = writebackColor(writeback?.status),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (!writeback?.errorMessage.isNullOrBlank()) {
                        Text(writeback?.errorMessage.orEmpty(), color = AudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!writeback?.writtenFields.isNullOrBlank()) {
                        MetadataLine("Written", writeback?.writtenFields.orEmpty())
                    }
                    if (!writeback?.catalogOnlyFields.isNullOrBlank()) {
                        MetadataLine("Catalog only", writeback?.catalogOnlyFields.orEmpty())
                    }
                    if (writebackRunning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = { showWriteConfirmation = true },
                        enabled = confirmedMetadata != null && writebackSupported && !writebackRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (writeback?.status == "Written") "Write tags again" else "Write accepted tags to file")
                    }
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            item { SectionHeading("Review suggestions") }
            items(suggestions, key = { "metadata-suggestion-${it.id}" }) { suggestion ->
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(suggestion.field.replaceFirstChar { it.uppercase() }, color = AudColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                                Text(suggestion.suggestedValue, fontWeight = FontWeight.SemiBold)
                            }
                            Text("${(suggestion.confidence * 100).toInt()}%", color = AudColors.Teal, style = MaterialTheme.typography.labelMedium)
                        }
                        Text("Source: ${suggestion.source}", color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = { onAcceptSuggestion(suggestion.id) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Accept")
                            }
                            OutlinedButton(onClick = { onIgnoreSuggestion(suggestion.id) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Ignore")
                            }
                        }
                    }
                }
            }
        }
        if (findings.isNotEmpty()) {
            item { SectionHeading("Doctor findings") }
            items(findings, key = { "track-finding-${it.id}" }) { finding ->
                Surface(color = AudColors.Surface, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(finding.issueType, fontWeight = FontWeight.Bold, color = AudColors.Amber)
                        Text(finding.explanation, color = AudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = track.mimeType.ifBlank { "audio/*" }
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(track.contentUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share track"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Share, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun MetadataEditorDialog(
    initial: TrackMetadataInput,
    onDismiss: () -> Unit,
    onSave: (TrackMetadataInput) -> Unit
) {
    var title by remember(initial.trackId) { mutableStateOf(initial.title) }
    var artist by remember(initial.trackId) { mutableStateOf(initial.artist) }
    var album by remember(initial.trackId) { mutableStateOf(initial.album) }
    var albumArtist by remember(initial.trackId) { mutableStateOf(initial.albumArtist) }
    var genre by remember(initial.trackId) { mutableStateOf(initial.genre) }
    var year by remember(initial.trackId) { mutableStateOf(initial.year?.toString().orEmpty()) }
    var trackNumber by remember(initial.trackId) { mutableStateOf(initial.trackNumber?.toString().orEmpty()) }
    var discNumber by remember(initial.trackId) { mutableStateOf(initial.discNumber?.toString().orEmpty()) }
    var language by remember(initial.trackId) { mutableStateOf(initial.language) }
    var mood by remember(initial.trackId) { mutableStateOf(initial.mood) }
    var energy by remember(initial.trackId) { mutableStateOf(initial.energy?.toString().orEmpty()) }
    var discoveryTags by remember(initial.trackId) { mutableStateOf(initial.discoveryTags) }
    val numericKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit metadata") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(albumArtist, { albumArtist = it }, label = { Text("Album artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(year, { year = it.filter { char -> char.isDigit() }.take(4) }, label = { Text("Year") }, keyboardOptions = numericKeyboard, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(trackNumber, { trackNumber = it.filter { char -> char.isDigit() }.take(3) }, label = { Text("Track") }, keyboardOptions = numericKeyboard, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(discNumber, { discNumber = it.filter { char -> char.isDigit() }.take(2) }, label = { Text("Disc") }, keyboardOptions = numericKeyboard, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(language, { language = it }, label = { Text("Language") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mood, { mood = it }, label = { Text("Mood") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    energy,
                    { energy = it.filter { char -> char.isDigit() }.take(3) },
                    label = { Text("Energy (0-100)") },
                    keyboardOptions = numericKeyboard,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    discoveryTags,
                    { discoveryTags = it },
                    label = { Text("Discovery tags") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        TrackMetadataInput(
                            trackId = initial.trackId,
                            title = title,
                            artist = artist,
                            album = album,
                            albumArtist = albumArtist,
                            genre = genre,
                            year = year.toIntOrNull(),
                            trackNumber = trackNumber.toIntOrNull(),
                            discNumber = discNumber.toIntOrNull(),
                            language = language,
                            mood = mood,
                            energy = energy.toIntOrNull(),
                            discoveryTags = discoveryTags
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(5.dp))
                Text("Save")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun writebackLabel(
    hasConfirmedMetadata: Boolean,
    supported: Boolean,
    running: Boolean,
    status: String?
): String = when {
    !supported -> "This file format remains catalog-only"
    running || status == "Writing" -> "Writing and verifying tags"
    status == "Written" -> "Verified in the audio file"
    status == "Partial" -> "Some fields are catalog-only"
    status == "Failed" -> "Writeback needs attention"
    status == "Pending" || hasConfirmedMetadata -> "Accepted changes are ready"
    else -> "Edit or accept metadata to enable"
}

private fun writebackColor(status: String?): Color = when (status) {
    "Written" -> AudColors.Teal
    "Partial" -> AudColors.Amber
    "Failed" -> AudColors.Coral
    else -> AudColors.TextSecondary
}

private fun UserConfirmedMetadataEntity.writebackPreviewLines(): List<String> = buildList {
    fun addText(label: String, value: String?) {
        if (value != null) add("$label: ${value.ifBlank { "(remove)" }}")
    }
    addText("Title", title)
    addText("Artist", artist)
    addText("Album", album)
    addText("Album artist", albumArtist)
    addText("Genre", genre)
    addText("Year", year?.toString())
    addText("Track number", trackNumber?.toString())
    addText("Disc number", discNumber?.toString())
    addText("Language", language)
    addText("Mood", mood)
    addText("Energy", energy?.toString())
    addText("Discovery tags", discoveryTags)
}

@Composable
private fun MetadataLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = AudColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.32f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.68f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun statusLabel(status: String): String = when (status) {
    "MissingMetadata" -> "Missing metadata"
    "PossibleDuplicate" -> "Possible duplicate"
    "NeedsReview" -> "Needs review"
    "LowConfidence" -> "Low-confidence match"
    "Failed" -> "Analysis failed"
    else -> status
}

private fun inferenceLabel(key: String): String = when (key.lowercase()) {
    "discoverytags", "tags" -> "Discovery tags"
    else -> key.replaceFirstChar { it.uppercase() }
}

private fun inboxStatusLabel(
    enhancementStatus: String,
    writebackStatus: String?,
    hasSuggestions: Boolean
): String = when {
    hasSuggestions -> "Metadata suggestions ready to review"
    writebackStatus == "Pending" -> "Metadata accepted - file writeback ready"
    writebackStatus == "Partial" -> "Some tags remain catalog-only"
    writebackStatus == "Failed" -> "File writeback needs attention"
    else -> statusLabel(enhancementStatus)
}
