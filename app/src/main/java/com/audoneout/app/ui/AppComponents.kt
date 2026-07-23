package com.audoneout.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.audoneout.app.data.PlaylistEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.recommendation.TrackRecommendation
import java.util.Locale

data class TrackCallbacks(
    val onOpen: (Long) -> Unit,
    val onToggleFavorite: (Long) -> Unit,
    val onStartRadio: (Long) -> Unit,
    val onAddToPlaylist: (Long, Long) -> Unit,
    val onDetails: (Long) -> Unit
)

@Composable
fun Artwork(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 6
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(AudColors.SurfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        if (uri.isNullOrBlank()) {
            Icon(Icons.Rounded.Album, contentDescription = contentDescription, tint = AudColors.TextMuted, modifier = Modifier.fillMaxSize(0.42f))
        } else {
            AsyncImage(
                model = uri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun TrackRow(
    track: TrackEntity,
    isFavorite: Boolean,
    playlists: List<PlaylistEntity>,
    callbacks: TrackCallbacks,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onRowClick: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .clickable { (onRowClick ?: callbacks.onOpen)(track.id) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(track.albumArtUri, track.title, Modifier.size(54.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title.ifBlank { track.fileName }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    supportingText ?: listOf(track.artist.ifBlank { "Unknown artist" }, track.album).filter { it.isNotBlank() }.joinToString(" - "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (track.format.isNotBlank()) Text(track.format.uppercase(), color = AudColors.Teal, style = MaterialTheme.typography.labelSmall)
                    Text(formatDuration(track.durationMs), color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = { callbacks.onToggleFavorite(track.id) }) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from Speed Dial" else "Add to Speed Dial",
                    tint = if (isFavorite) AudColors.Coral else AudColors.TextSecondary
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open in music player") },
                        leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                        onClick = { menuOpen = false; callbacks.onOpen(track.id) }
                    )
                    DropdownMenuItem(
                        text = { Text("Start radio") },
                        leadingIcon = { Icon(Icons.Rounded.Radio, null) },
                        onClick = { menuOpen = false; callbacks.onStartRadio(track.id) }
                    )
                    playlists.take(5).forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to ${playlist.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) },
                            onClick = { menuOpen = false; callbacks.onAddToPlaylist(playlist.id, track.id) }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Track details") },
                        leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                        onClick = { menuOpen = false; callbacks.onDetails(track.id) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menuOpen = false
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = track.mimeType.ifBlank { "audio/*" }
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(track.contentUri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share ${track.title}"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedDialTile(
    track: TrackEntity,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector = Icons.Rounded.OpenInNew,
    actionDescription: String = "Open in music player"
) {
    Column(
        modifier = modifier.clickable { onOpen(track.id) },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            Artwork(track.albumArtUri, track.title, Modifier.fillMaxWidth().aspectRatio(1f))
            Surface(
                color = Color.Black.copy(alpha = 0.66f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp)
            ) {
                Icon(actionIcon, "$actionDescription: ${track.title}", tint = Color.White, modifier = Modifier.padding(5.dp).size(18.dp))
            }
        }
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        Text(track.artist.ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun RecommendationRow(
    recommendation: TrackRecommendation,
    isFavorite: Boolean,
    playlists: List<PlaylistEntity>,
    callbacks: TrackCallbacks
) {
    TrackRow(
        track = recommendation.track,
        isFavorite = isFavorite,
        playlists = playlists,
        callbacks = callbacks,
        supportingText = recommendation.reasons.joinToString(" - ")
    )
}

@Composable
fun EmptyPanel(icon: @Composable () -> Unit, title: String, body: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AudColors.Surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = AudColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SectionHeading(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun Metric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = AudColors.Surface, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = AudColors.TextSecondary)
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
