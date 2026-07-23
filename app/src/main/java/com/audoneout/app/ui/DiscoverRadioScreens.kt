package com.audoneout.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audoneout.app.MainUiState
import com.audoneout.app.data.PlaylistEntity
import com.audoneout.app.data.RadioStationEntity
import com.audoneout.app.lastfm.LastFmRecommendation

@Composable
fun DiscoverScreen(
    state: MainUiState,
    callbacks: TrackCallbacks,
    onRefreshLocal: () -> Unit,
    onOpenMix: () -> Unit,
    onOpenMixtape: () -> Unit,
    onRefreshLastFm: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocalMatch: (LastFmRecommendation) -> Unit,
    onOpenLastFmPage: (String) -> Unit,
    onOpenYoutubeMusic: (LastFmRecommendation) -> Unit
) {
    var selectedMode by remember { mutableIntStateOf(0) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TabRow(selectedTabIndex = selectedMode, containerColor = Color.Transparent) {
                Tab(
                    selected = selectedMode == 0,
                    onClick = { selectedMode = 0 },
                    text = { Text("Local") },
                    icon = { Icon(Icons.Rounded.LibraryMusic, null) }
                )
                Tab(
                    selected = selectedMode == 1,
                    onClick = { selectedMode = 1 },
                    text = { Text("Last.fm") },
                    icon = { Icon(Icons.Rounded.Cloud, null) }
                )
            }
        }
        if (selectedMode == 0) {
            item {
                SectionHeading("For You") {
                    IconButton(onClick = onRefreshLocal) { Icon(Icons.Rounded.Refresh, "Refresh local recommendations") }
                }
            }
            item {
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = AudColors.Teal, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Offline recommendations", fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.localLastFmTasteSeedCount > 0) {
                                        "Corrected local metadata, Speed Dial, and ${state.localLastFmTasteSeedCount} Last.fm taste matches"
                                    } else {
                                        "Corrected local metadata, Speed Dial, folders, formats, and controlled exploration"
                                    },
                                    color = AudColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onOpenMix, enabled = state.recommendations.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.OpenInNew, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open mix")
                            }
                            OutlinedButton(onClick = onOpenMixtape, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.AutoAwesome, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Create mixtape")
                            }
                        }
                    }
                }
            }
            if (state.recommendations.isEmpty()) {
                item {
                    EmptyPanel(
                        icon = { Icon(Icons.Rounded.MusicNote, null, tint = AudColors.TextMuted, modifier = Modifier.size(34.dp)) },
                        title = "No recommendations yet",
                        body = "Scan music and pin a few favorites to shape your first mix."
                    )
                }
            } else {
                items(state.recommendations, key = { "recommendation-${it.track.id}" }) { recommendation ->
                    RecommendationRow(recommendation, recommendation.track.id in state.favoriteIds, state.playlists, callbacks)
                }
            }
        } else {
            item {
                SectionHeading("From your scrobbles") {
                    IconButton(
                        onClick = onRefreshLastFm,
                        enabled = !state.lastFmLoading &&
                            state.settings.onlineEnrichmentEnabled &&
                            state.settings.lastFmEnabled
                    ) { Icon(Icons.Rounded.Refresh, "Refresh Last.fm recommendations") }
                }
            }
            when {
                !state.settings.onlineEnrichmentEnabled -> {
                    item {
                        EmptyPanel(
                            icon = { Icon(Icons.Rounded.CloudOff, null, tint = AudColors.Coral, modifier = Modifier.size(34.dp)) },
                            title = "Online enrichment is off",
                            body = "Enable it in Settings before AudOneOut contacts Last.fm."
                        )
                    }
                    item {
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Settings, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Open online settings")
                        }
                    }
                }
                !state.settings.lastFmEnabled || state.settings.lastFmUsername.isBlank() || state.settings.lastFmApiKey.isBlank() -> {
                    item {
                        EmptyPanel(
                            icon = { Icon(Icons.Rounded.Cloud, null, tint = AudColors.Coral, modifier = Modifier.size(34.dp)) },
                            title = "Connect a Last.fm profile",
                            body = "Add your username and API key in Settings."
                        )
                    }
                    item {
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Settings, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Open Last.fm settings")
                        }
                    }
                }
                state.lastFmLoading -> {
                    item {
                        Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Reading your public Last.fm taste profile", color = AudColors.TextSecondary)
                            }
                        }
                    }
                }
                state.lastFmError.isNotBlank() -> {
                    item {
                        EmptyPanel(
                            icon = { Icon(Icons.Rounded.CloudOff, null, tint = AudColors.Coral, modifier = Modifier.size(34.dp)) },
                            title = "Last.fm sync needs attention",
                            body = state.lastFmError
                        )
                    }
                    item {
                        OutlinedButton(onClick = onRefreshLastFm, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Try again")
                        }
                    }
                }
                else -> {
                    state.lastFmProfile?.let { profile ->
                        item {
                            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Artwork(profile.imageUrl, profile.displayName, Modifier.size(58.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(profile.displayName.ifBlank { profile.username }, fontWeight = FontWeight.Bold)
                                        Text("${profile.playCount} scrobbles", color = AudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        Text("${state.recentLastFmScrobbleCount} recent scrobbles shaped this refresh", color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                                        Text("${state.lastFmRecommendations.count { it.localTrack != null }} recommendations in your library", color = AudColors.Teal, style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { onOpenLastFmPage(profile.profileUrl) }) {
                                        Icon(Icons.Rounded.OpenInNew, "Open Last.fm profile")
                                    }
                                }
                            }
                        }
                    }
                    if (state.lastFmRecommendations.isEmpty()) {
                        item {
                            EmptyPanel(
                                icon = { Icon(Icons.Rounded.MusicNote, null, tint = AudColors.TextMuted, modifier = Modifier.size(34.dp)) },
                                title = "No Last.fm matches yet",
                                body = "Refresh after your Last.fm profile has more listening history."
                            )
                        }
                    } else {
                        items(state.lastFmRecommendations, key = { "lastfm-${it.track.artist}-${it.track.name}" }) { recommendation ->
                            LastFmRecommendationCard(
                                recommendation = recommendation,
                                onOpenLocalMatch = onOpenLocalMatch,
                                onOpenLastFmPage = onOpenLastFmPage,
                                onOpenYoutubeMusic = onOpenYoutubeMusic
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LastFmRecommendationCard(
    recommendation: LastFmRecommendation,
    onOpenLocalMatch: (LastFmRecommendation) -> Unit,
    onOpenLastFmPage: (String) -> Unit,
    onOpenYoutubeMusic: (LastFmRecommendation) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    recommendation.localTrack?.albumArtUri ?: recommendation.track.imageUrl,
                    recommendation.track.name,
                    Modifier.size(58.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(recommendation.track.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(recommendation.track.artist, color = AudColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(recommendation.reason, color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (recommendation.localTrack != null) {
                    Icon(Icons.Rounded.CheckCircle, "Available locally", tint = AudColors.Teal)
                }
            }
            if (recommendation.localTrack != null) {
                Button(onClick = { onOpenLocalMatch(recommendation) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.OpenInNew, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open local track in player")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onOpenLastFmPage(recommendation.track.url) },
                    enabled = recommendation.track.url.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Last.fm")
                }
                OutlinedButton(onClick = { onOpenYoutubeMusic(recommendation) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.SmartDisplay, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("YouTube Music")
                }
            }
        }
    }
}

@Composable
fun RadioScreen(
    state: MainUiState,
    callbacks: TrackCallbacks,
    onStartPlaylistRadio: (Long) -> Unit,
    onSaveStation: (String, String) -> Unit,
    onOpenStation: (RadioStationEntity) -> Unit,
    onDeleteStation: (Long) -> Unit
) {
    var selectedMode by remember { mutableIntStateOf(0) }
    var stationName by remember { mutableStateOf("") }
    var stationUrl by remember { mutableStateOf("") }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TabRow(selectedTabIndex = selectedMode, containerColor = Color.Transparent) {
                Tab(selectedMode == 0, onClick = { selectedMode = 0 }, text = { Text("Local radio") }, icon = { Icon(Icons.Rounded.Radio, null) })
                Tab(selectedMode == 1, onClick = { selectedMode = 1 }, text = { Text("Online radio") }, icon = { Icon(Icons.Rounded.Cloud, null) })
            }
        }
        if (selectedMode == 0) {
            item { RadioSectionHeading("Start from a favorite") }
            if (state.favorites.isEmpty()) {
                item {
                    EmptyPanel(
                        icon = { Icon(Icons.Rounded.Favorite, null, tint = AudColors.Coral, modifier = Modifier.size(32.dp)) },
                        title = "Pin a seed song",
                        body = "Heart a song in Library, then send a related local mix to your music player.",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(state.favorites.take(9).chunked(3)) { tracks ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        tracks.forEach { track ->
                            SpeedDialTile(
                                track = track,
                                onOpen = callbacks.onStartRadio,
                                modifier = Modifier.weight(1f),
                                actionIcon = Icons.Rounded.Radio,
                                actionDescription = "Start local radio"
                            )
                        }
                        repeat(3 - tracks.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item { RadioSectionHeading("Start from a playlist") }
            if (state.playlists.isEmpty()) {
                item {
                    Text("Create a playlist in Library to use it as a radio seed.", color = AudColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                }
            } else {
                items(state.playlists, key = { "radio-playlist-${it.id}" }) { playlist ->
                    RadioSeedRow(playlist, onStartPlaylistRadio)
                }
            }
            if (state.localRadio.isNotEmpty()) {
                item { RadioSectionHeading("Radio from ${state.radioSeedLabel}") }
                items(state.localRadio.take(30), key = { "radio-track-${it.track.id}" }) { recommendation ->
                    RecommendationRow(recommendation, recommendation.track.id in state.favoriteIds, state.playlists, callbacks)
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = AudColors.Surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add a stream", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = stationName,
                            onValueChange = { stationName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Station name") }
                        )
                        OutlinedTextField(
                            value = stationUrl,
                            onValueChange = { stationUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Link, null) },
                            label = { Text("Stream or M3U URL") }
                        )
                        Button(
                            onClick = {
                                onSaveStation(stationName, stationUrl)
                                stationName = ""
                                stationUrl = ""
                            },
                            enabled = stationUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save station")
                        }
                    }
                }
            }
            item { RadioSectionHeading("Saved stations") }
            if (state.radioStations.isEmpty()) {
                item {
                    EmptyPanel(
                        icon = { Icon(Icons.Rounded.Radio, null, tint = AudColors.Teal, modifier = Modifier.size(34.dp)) },
                        title = "No online stations",
                        body = "Add a direct radio stream or M3U address.",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(state.radioStations, key = { it.id }) { station ->
                    StationRow(station, onOpenStation, onDeleteStation)
                }
            }
        }
    }
}

@Composable
private fun RadioSectionHeading(title: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionHeading(title)
    }
}

@Composable
private fun RadioSeedRow(playlist: PlaylistEntity, onStart: (Long) -> Unit) {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().clickable { onStart(playlist.id) }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = AudColors.SurfaceRaised, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LibraryMusic, null) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, fontWeight = FontWeight.SemiBold)
                Text("Generate related local tracks", color = AudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.Radio, "Start radio", tint = AudColors.Coral)
        }
    }
}

@Composable
private fun StationRow(station: RadioStationEntity, onOpen: (RadioStationEntity) -> Unit, onDelete: (Long) -> Unit) {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().clickable { onOpen(station) }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = AudColors.SurfaceRaised, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = AudColors.Teal) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(station.streamUrl, color = AudColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onDelete(station.id) }) { Icon(Icons.Rounded.DeleteOutline, "Delete station") }
            IconButton(onClick = { onOpen(station) }) { Icon(Icons.Rounded.OpenInNew, "Open station in music player") }
        }
    }
}

@Composable
fun MixtapeScreen(
    state: MainUiState,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onOpen: () -> Unit,
    onSave: (String) -> Unit
) {
    var saveName by remember { mutableStateOf("AudOneOut Mixtape") }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.mixtapePrompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Describe your mixtape") },
                placeholder = { Text("45 minutes of Bengali rock without repeating artists") }
            )
        }
        item {
            Button(onClick = onGenerate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Interpret and generate locally")
            }
        }
        item { Text(state.mixtapeMessage, color = AudColors.TextSecondary) }
        state.mixtapeRules?.let { rules ->
            item {
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Interpreted rules", fontWeight = FontWeight.Bold)
                        RuleText("Duration", rules.targetDurationMinutes?.let { "$it minutes" } ?: "Flexible")
                        RuleText("Genres", rules.includedGenres.ifEmpty { listOf("Any") }.joinToString())
                        if (rules.excludedGenres.isNotEmpty()) {
                            RuleText("Avoid genres", rules.excludedGenres.joinToString())
                        }
                        RuleText("Languages", rules.languages.ifEmpty { listOf("Any") }.joinToString())
                        if (rules.folders.isNotEmpty()) {
                            RuleText("Folders", rules.folders.joinToString())
                        }
                        RuleText("Formats", rules.fileFormats.ifEmpty { listOf("Any") }.joinToString())
                        rules.energyRange?.let { energyRange ->
                            RuleText("Energy", "${energyRange.first}-${energyRange.last}")
                        }
                        if (rules.addedWithinDays != null) {
                            RuleText("Added", "Last ${rules.addedWithinDays} days")
                        }
                        RuleText("Artist repeats", if (rules.avoidArtistRepetitions) "Avoid" else "Allowed")
                    }
                }
            }
        }
        if (state.mixtapeCandidates.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.OpenInNew, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open in player")
                    }
                    OutlinedButton(onClick = { onSave(saveName) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            }
            item {
                OutlinedTextField(value = saveName, onValueChange = { saveName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Playlist name") })
            }
            item { SectionHeading("Selected tracks") }
            items(state.mixtapeCandidates, key = { "mixtape-${it.track.libraryId}" }) { candidate ->
                Surface(color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(candidate.track.albumArtUri, candidate.track.title, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(candidate.track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(candidate.reasons.joinToString(" - "), color = AudColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleText(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = AudColors.TextMuted, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.bodySmall)
        Text(value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodySmall)
    }
}
