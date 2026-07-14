package com.audoneout.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.audoneout.app.data.AlbumEntity
import com.audoneout.app.data.AnalysisResultEntity
import com.audoneout.app.data.ArtistEntity
import com.audoneout.app.data.FolderBlacklistRuleEntity
import com.audoneout.app.data.FolderEntity
import com.audoneout.app.data.MusicRootEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.scan.MediaStoreChangeObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var mediaStoreChangeObserver: MediaStoreChangeObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudOneOutTheme {
                AudOneOutApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mediaStoreChangeObserver.register()
    }

    override fun onStop() {
        mediaStoreChangeObserver.unregister()
        super.onStop()
    }
}

private sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: UiIcon
) {
    data object Home : AppDestination("home", "Home", UiIcon.Home)
    data object Library : AppDestination("library", "Library", UiIcon.Library)
    data object Player : AppDestination("player", "Player", UiIcon.Player)
    data object Inbox : AppDestination("inbox", "Inbox", UiIcon.Inbox)
    data object Settings : AppDestination("settings", "Settings", UiIcon.Settings)
}

private enum class UiIcon {
    Home,
    Library,
    Player,
    Inbox,
    Settings,
    NewMusic,
    Health,
    Duplicate,
    Mixtape,
    Playback,
    Queue,
    Pipeline,
    Review,
    Root,
    BlacklistOn,
    BlacklistOff,
    Track,
    EmptyLibrary
}

private val bottomDestinations = listOf(
    AppDestination.Home,
    AppDestination.Library,
    AppDestination.Player,
    AppDestination.Inbox,
    AppDestination.Settings
)

@Composable
private fun AudOneOutApp(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selected = bottomDestinations.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    } ?: AppDestination.Home

    Scaffold(
        topBar = { AudOneOutTopBar(selected.label) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(AppDestination.Inbox.route) },
                containerColor = AudOneOutColors.accentCoral,
                contentColor = Color.White
            ) {
                CanvasIcon(icon = UiIcon.Mixtape, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        },
        bottomBar = {
            NavigationBar(containerColor = AudOneOutColors.surface, tonalElevation = 0.dp) {
                bottomDestinations.forEach { destination ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            SymbolBadge(
                                icon = destination.icon,
                                contentDescription = destination.label,
                                selected = isSelected
                            )
                        },
                        label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        },
        containerColor = AudOneOutColors.background
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(contentPadding)
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(uiState, onScan = viewModel::scanLibrary, onHealthCheck = viewModel::runHealthCheck)
            }
            composable(AppDestination.Library.route) {
                LibraryScreen(
                    uiState = uiState,
                    onViewChange = viewModel::setLibraryView,
                    onSortChange = viewModel::setLibrarySort,
                    onQueryChange = viewModel::setLibraryQuery,
                    onToggleGrid = viewModel::toggleLibraryGridMode
                )
            }
            composable(AppDestination.Player.route) {
                PlayerScreen(uiState)
            }
            composable(AppDestination.Inbox.route) {
                InboxScreen(uiState, onHealthCheck = viewModel::runHealthCheck)
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    uiState = uiState,
                    onScan = viewModel::scanLibrary,
                    onAddRoot = viewModel::addRoot,
                    onRootIncludedChange = viewModel::setRootIncluded,
                    onRescanRoot = viewModel::rescanRoot,
                    onRemoveRoot = viewModel::removeRoot,
                    onAddRule = viewModel::addBlacklistRule,
                    onRuleEnabledChange = viewModel::setBlacklistRuleEnabled,
                    onDeleteRule = viewModel::deleteBlacklistRule,
                    onRestoreDefaultRules = viewModel::restoreDefaultBlacklist
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudOneOutTopBar(title: String) {
    TopAppBar(
        title = {
            Column {
                Text("AudOneOut", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.labelMedium, color = AudOneOutColors.textMuted)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AudOneOutColors.background,
            titleContentColor = AudOneOutColors.textPrimary
        )
    )
}

@Composable
private fun HomeScreen(uiState: MainUiState, onScan: () -> Unit, onHealthCheck: () -> Unit) {
    ScreenFrame {
        HeroPanel()
        PermissionScanPanel(onScan)
        CardPanel {
            Text("Library Doctor", color = AudOneOutColors.textPrimary, fontWeight = FontWeight.Bold)
            Text("${uiState.analysisResults.size} active findings across metadata, duplicates, artwork, and file health.", color = AudOneOutColors.textSecondary)
            OutlinedButton(onClick = onHealthCheck, enabled = !uiState.busy) { Text("Run Health Check") }
        }
        MetricGrid(uiState.library)
        ProgressPanel(uiState)
        SectionTitle("Library Intelligence")
        FeatureRow(UiIcon.NewMusic, "New music detected", "${uiState.inbox.size} tracks waiting for enhancement")
        FeatureRow(UiIcon.Health, "Library health score", "${uiState.health.score}/100 with ${uiState.health.issueCount} review items")
        FeatureRow(UiIcon.Duplicate, "Duplicate estimate", "${uiState.health.duplicateCandidates} duplicate groups detected")
        FeatureRow(UiIcon.Mixtape, "Suggested mixtapes", "Create prompt-based playlists from local metadata")
    }
}

@Composable
private fun LibraryScreen(
    uiState: MainUiState,
    onViewChange: (LibraryView) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleGrid: () -> Unit
) {
    ScreenFrame {
        SectionTitle("Library")
        MetricGrid(uiState.library)
        LibraryControlsPanel(uiState, onViewChange, onSortChange, onQueryChange, onToggleGrid)
        when (uiState.libraryView) {
            LibraryView.Songs -> SongLibrary(uiState)
            LibraryView.Albums -> AlbumLibrary(uiState)
            LibraryView.Artists -> ArtistLibrary(uiState)
            LibraryView.Folders -> FolderLibrary(uiState)
        }
    }
}

@Composable
private fun LibraryControlsPanel(
    uiState: MainUiState,
    onViewChange: (LibraryView) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleGrid: () -> Unit
) {
    CardPanel {
        OutlinedTextField(
            value = uiState.libraryQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search library") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LibraryView.entries.forEach { view ->
                ToggleTextButton(
                    text = view.name,
                    selected = uiState.libraryView == view,
                    modifier = Modifier.weight(1f),
                    onClick = { onViewChange(view) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(LibrarySort.Title, LibrarySort.Artist, LibrarySort.Album, LibrarySort.RecentlyAdded).forEach { sort ->
                ToggleTextButton(
                    text = sort.label,
                    selected = uiState.librarySort == sort,
                    modifier = Modifier.weight(1f),
                    onClick = { onSortChange(sort) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToggleTextButton(
                text = "Duration",
                selected = uiState.librarySort == LibrarySort.Duration,
                modifier = Modifier.weight(1f),
                onClick = { onSortChange(LibrarySort.Duration) }
            )
            ToggleTextButton(
                text = "Size",
                selected = uiState.librarySort == LibrarySort.Size,
                modifier = Modifier.weight(1f),
                onClick = { onSortChange(LibrarySort.Size) }
            )
            ToggleTextButton(
                text = if (uiState.libraryGridMode) "Grid" else "List",
                selected = uiState.libraryGridMode,
                modifier = Modifier.weight(1f),
                onClick = onToggleGrid
            )
        }
    }
}

@Composable
private fun ToggleTextButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container = if (selected) AudOneOutColors.accentCoral else AudOneOutColors.surfaceAlt
    val textColor = if (selected) Color.White else AudOneOutColors.textSecondary
    TextButton(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
    ) {
        Text(text, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SongLibrary(uiState: MainUiState) {
    if (uiState.tracks.isEmpty()) {
        EmptyState(UiIcon.EmptyLibrary, "No songs indexed yet", "Grant media access, choose roots, and scan with MediaStore.")
    } else if (uiState.libraryGridMode) {
        TwoColumnGrid(uiState.tracks.take(40)) { track ->
            TrackTile(track)
        }
    } else {
        uiState.tracks.take(80).forEach { track ->
            TrackRow(track)
        }
    }
}

@Composable
private fun AlbumLibrary(uiState: MainUiState) {
    if (uiState.albums.isEmpty()) {
        EmptyState(UiIcon.Library, "No albums yet", "Albums appear after scan facets are built from indexed tracks.")
    } else if (uiState.libraryGridMode) {
        TwoColumnGrid(uiState.albums.take(40)) { album ->
            AlbumTile(album)
        }
    } else {
        uiState.albums.take(80).forEach { album ->
            AlbumRow(album)
        }
    }
}

@Composable
private fun ArtistLibrary(uiState: MainUiState) {
    if (uiState.artists.isEmpty()) {
        EmptyState(UiIcon.Library, "No artists yet", "Artist summaries appear after your first scan.")
    } else {
        uiState.artists.take(80).forEach { artist ->
            ArtistRow(artist)
        }
    }
}

@Composable
private fun FolderLibrary(uiState: MainUiState) {
    if (uiState.folders.isEmpty()) {
        EmptyState(UiIcon.Root, "No folders yet", "Folder summaries appear after scanning approved music roots.")
    } else {
        uiState.folders.take(80).forEach { folder ->
            FolderRow(folder)
        }
    }
}

@Composable
private fun PlayerScreen(uiState: MainUiState) {
    ScreenFrame {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(AudOneOutColors.accentPurple, AudOneOutColors.accentCoral))),
            contentAlignment = Alignment.Center
        ) {
            WaveformMark()
        }
        Text(uiState.nowPlayingTitle, color = AudOneOutColors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        FeatureRow(UiIcon.Playback, "Media3 playback", "Background playback service is installed; queue controls connect after scanned tracks are selected.")
        FeatureRow(UiIcon.Queue, "Queue and playlists", "Play next, add to queue, and save queue build on the indexed library.")
    }
}

@Composable
private fun InboxScreen(uiState: MainUiState, onHealthCheck: () -> Unit) {
    ScreenFrame {
        SectionTitle("New Music Inbox")
        CardPanel {
            Text("Review queue", color = AudOneOutColors.textPrimary, fontWeight = FontWeight.Bold)
            Text("${uiState.inbox.size} tracks need attention; ${uiState.analysisResults.size} Doctor findings are open.", color = AudOneOutColors.textSecondary)
            OutlinedButton(onClick = onHealthCheck, enabled = !uiState.busy) { Text("Run Library Doctor") }
        }
        if (uiState.inbox.isEmpty()) {
            EmptyState(UiIcon.Review, "No tracks need review", "New, low-confidence, duplicate, and missing-metadata tracks will appear here.")
        } else {
            uiState.inbox.forEach { track ->
                TrackRow(track, subtitle = "${track.enhancementStatus} - ${track.relativePath}")
            }
        }
        if (uiState.analysisResults.isNotEmpty()) {
            SectionTitle("Doctor Findings")
            uiState.analysisResults.take(40).forEach { result ->
                AnalysisResultRow(result)
            }
        }
        FeatureRow(UiIcon.Pipeline, "Enhancement pipeline", "Basic indexing, health checks, discovery tags, and optional online enrichment are staged separately.")
        FeatureRow(UiIcon.Review, "Review before writing", "AudOneOut keeps original, enriched, and user-confirmed metadata layers separate.")
    }
}

@Composable
private fun SettingsScreen(
    uiState: MainUiState,
    onScan: () -> Unit,
    onAddRoot: (String, String, String) -> Unit,
    onRootIncludedChange: (Long, Boolean) -> Unit,
    onRescanRoot: (Long) -> Unit,
    onRemoveRoot: (Long) -> Unit,
    onAddRule: (String) -> Unit,
    onRuleEnabledChange: (Long, Boolean) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onRestoreDefaultRules: () -> Unit
) {
    val context = LocalContext.current
    var typedRule by remember { mutableStateOf("") }
    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val root = uri.toMusicRootSelection()
            onAddRoot(root.displayName, root.uri, root.location)
        }
    }
    ScreenFrame {
        SectionTitle("Music Roots")
        CardPanel {
            Text("Scan scope", color = AudOneOutColors.textPrimary, fontWeight = FontWeight.Bold)
            Text("Pick the folders that contain your music. AudOneOut stores read access and never requests all-files storage.", color = AudOneOutColors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { rootPicker.launch(null) }) { Text("Add Root") }
                OutlinedButton(onClick = onScan) { Text("Scan Included") }
            }
        }
        if (uiState.roots.isEmpty()) {
            EmptyState(UiIcon.Root, "No roots selected", "If you scan now, AudOneOut uses MediaStore audio. Add roots to narrow future scans.")
        } else {
            uiState.roots.forEach { root ->
                MusicRootRow(
                    root = root,
                    onIncludedChange = { included -> onRootIncludedChange(root.id, included) },
                    onRescan = { onRescanRoot(root.id) },
                    onRemove = { onRemoveRoot(root.id) }
                )
            }
        }
        PermissionScanPanel(onScan)
        SectionTitle("Folder Blacklist")
        CardPanel {
            Text("Excluded folders", color = AudOneOutColors.textPrimary, fontWeight = FontWeight.Bold)
            Text("Blacklisting only removes tracks from AudOneOut's working library. It never deletes files.", color = AudOneOutColors.textSecondary)
            OutlinedTextField(
                value = typedRule,
                onValueChange = { typedRule = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Folder name rule") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val rule = typedRule.trim()
                        if (rule.isNotBlank()) {
                            onAddRule(rule)
                            typedRule = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Add Rule") }
                OutlinedButton(onClick = onRestoreDefaultRules, modifier = Modifier.weight(1f)) {
                    Text("Restore Defaults")
                }
            }
        }
        uiState.blacklistRules.take(24).forEach { rule ->
            BlacklistRuleRow(
                rule = rule,
                onEnabledChange = { enabled -> onRuleEnabledChange(rule.id, enabled) },
                onDelete = { onDeleteRule(rule.id) }
            )
        }
        SectionTitle("Background Checks")
        ToggleRow("Automatic library checking", uiState.settings.automaticLibraryChecking)
        ToggleRow("Wi-Fi only for online enrichment", uiState.settings.wifiOnlyOnlineEnrichment)
        ToggleRow("Analyse only while charging", uiState.settings.analyseOnlyWhileCharging)
        ToggleRow("Quiet background mode", uiState.settings.quietBackgroundMode)
    }
}

@Composable
private fun PermissionScanPanel(onScan: () -> Unit) {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onScan()
    }
    CardPanel {
        Text("Local music access", color = AudOneOutColors.textPrimary, fontWeight = FontWeight.Bold)
        Text("AudOneOut uses MediaStore and never requests broad all-files access.", color = AudOneOutColors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { launcher.launch(permission) }) { Text("Grant & Scan") }
            OutlinedButton(onClick = onScan) { Text("Check Now") }
        }
    }
}

@Composable
private fun ProgressPanel(uiState: MainUiState) {
    CardPanel {
        Text("Scan progress", color = AudOneOutColors.textPrimary, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            "${uiState.scanProgress.estimatedRemainingWork}: ${uiState.scanProgress.tracksFound} found, ${uiState.scanProgress.excludedTracks} excluded",
            color = AudOneOutColors.textSecondary
        )
        if (uiState.scanProgress.currentFolder.isNotBlank()) {
            Text(uiState.scanProgress.currentFolder, color = AudOneOutColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrackRow(track: TrackEntity, subtitle: String = "${track.artist} - ${track.album}") {
    CardPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolBadge(UiIcon.Track, track.title)
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title.ifBlank { track.fileName }, color = AudOneOutColors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = AudOneOutColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatDuration(track.durationMs), color = AudOneOutColors.textMuted)
        }
    }
}

@Composable
private fun TrackTile(track: TrackEntity) {
    CardPanel {
        SymbolBadge(UiIcon.Track, track.title)
        Text(track.title.ifBlank { track.fileName }, color = AudOneOutColors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist.ifBlank { "Unknown Artist" }, color = AudOneOutColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${formatDuration(track.durationMs)} - ${track.format.ifBlank { track.mimeType.substringAfterLast('/') }}", color = AudOneOutColors.textMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AlbumRow(album: AlbumEntity) {
    FeatureRow(
        UiIcon.Library,
        album.title,
        "${album.artistName} - ${album.trackCount} tracks - ${formatDuration(album.durationMs)} - ${formatBytes(album.storageBytes)}"
    )
}

@Composable
private fun AlbumTile(album: AlbumEntity) {
    CardPanel {
        SymbolBadge(UiIcon.Library, album.title)
        Text(album.title, color = AudOneOutColors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(album.artistName, color = AudOneOutColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${album.trackCount} tracks - ${formatBytes(album.storageBytes)}", color = AudOneOutColors.textMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ArtistRow(artist: ArtistEntity) {
    FeatureRow(
        UiIcon.NewMusic,
        artist.name,
        "${artist.trackCount} tracks - ${artist.albumCount} albums - ${formatDuration(artist.durationMs)}"
    )
}

@Composable
private fun FolderRow(folder: FolderEntity) {
    FeatureRow(
        UiIcon.Root,
        folder.name,
        "${folder.trackCount} tracks - ${formatBytes(folder.storageBytes)} - ${folder.path}"
    )
}

@Composable
private fun AnalysisResultRow(result: AnalysisResultEntity) {
    val icon = when {
        result.issueType.contains("duplicate", ignoreCase = true) -> UiIcon.Duplicate
        result.issueType.contains("artwork", ignoreCase = true) -> UiIcon.Library
        result.severity.equals("warning", ignoreCase = true) -> UiIcon.Health
        else -> UiIcon.Review
    }
    FeatureRow(
        icon,
        result.issueType,
        "${result.severity.uppercase()} - ${result.explanation}"
    )
}

@Composable
private fun <T> TwoColumnGrid(items: List<T>, itemContent: @Composable (T) -> Unit) {
    items.chunked(2).forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            rowItems.forEach { item ->
                Box(modifier = Modifier.weight(1f)) {
                    itemContent(item)
                }
            }
            if (rowItems.size == 1) {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MusicRootRow(
    root: MusicRootEntity,
    onIncludedChange: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onRemove: () -> Unit
) {
    CardPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolBadge(UiIcon.Root, root.displayName)
            Column(modifier = Modifier.weight(1f)) {
                Text(root.displayName, color = AudOneOutColors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(root.location.ifBlank { root.uri }, color = AudOneOutColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = root.included, onCheckedChange = onIncludedChange)
        }
        Text(
            "${root.indexedTrackCount} tracks - ${formatBytes(root.storageBytes)} - ${root.scanStatus} - ${formatRelativeTimestamp(root.lastScanTimeMillis)}",
            color = AudOneOutColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRescan, enabled = root.included) { Text("Rescan") }
            OutlinedButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun BlacklistRuleRow(
    rule: FolderBlacklistRuleEntity,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    CardPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolBadge(if (rule.enabled) UiIcon.BlacklistOn else UiIcon.BlacklistOff, rule.label)
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.label, color = AudOneOutColors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${rule.matchType}: ${rule.pattern}", color = AudOneOutColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
        }
        Text("Preview: ${rule.excludedPreviewCount} indexed tracks match this rule", color = AudOneOutColors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AudOneOutColors.textPrimary)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ScreenFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}

@Composable
private fun HeroPanel() {
    CardPanel {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(AudOneOutColors.accentCoral, AudOneOutColors.accentPurple))),
            contentAlignment = Alignment.Center
        ) {
            CanvasIcon(icon = UiIcon.Mixtape, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Text("Supercharge your local music.", color = AudOneOutColors.textPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Scan files, clean metadata, play locally, build queues, create mixtapes, and export portable playlists.", color = AudOneOutColors.textSecondary)
    }
}

@Composable
private fun MetricGrid(snapshot: LibrarySnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard("Tracks", snapshot.trackCount.toString(), Modifier.weight(1f))
        MetricCard("Albums", snapshot.albumCount.toString(), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard("Artists", snapshot.artistCount.toString(), Modifier.weight(1f))
        MetricCard("Folders", snapshot.folderCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AudOneOutColors.surfaceAlt),
        border = BorderStroke(1.dp, AudOneOutColors.line)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, color = AudOneOutColors.textPrimary, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = AudOneOutColors.textMuted, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FeatureRow(icon: UiIcon, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AudOneOutColors.surfaceAlt)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SymbolBadge(icon = icon, contentDescription = title)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AudOneOutColors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(body, color = AudOneOutColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CardPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AudOneOutColors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = AudOneOutColors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyState(icon: UiIcon, title: String, body: String) {
    CardPanel {
        SymbolBadge(icon = icon, contentDescription = title)
        Text(title, color = AudOneOutColors.textPrimary, style = MaterialTheme.typography.titleMedium)
        Text(body, color = AudOneOutColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SymbolBadge(icon: UiIcon, contentDescription: String, selected: Boolean = false) {
    val background = if (selected) AudOneOutColors.accentCoral else AudOneOutColors.accentPurple.copy(alpha = 0.18f)
    val foreground = if (selected) Color.White else AudOneOutColors.accentCoral
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(background)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        CanvasIcon(icon = icon, tint = foreground, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CanvasIcon(icon: UiIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawUiIcon(icon = icon, tint = tint)
    }
}

private fun DrawScope.drawUiIcon(icon: UiIcon, tint: Color) {
    val strokeWidth = size.minDimension * 0.09f
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val w = size.width
    val h = size.height
    fun p(x: Float, y: Float) = Offset(w * x, h * y)

    when (icon) {
        UiIcon.Home -> {
            drawLine(tint, p(0.18f, 0.50f), p(0.50f, 0.20f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.50f, 0.20f), p(0.82f, 0.50f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.28f, 0.48f), p(0.28f, 0.82f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.72f, 0.48f), p(0.72f, 0.82f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.28f, 0.82f), p(0.72f, 0.82f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.Library, UiIcon.EmptyLibrary -> {
            drawLine(tint, p(0.24f, 0.20f), p(0.24f, 0.82f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.46f, 0.20f), p(0.46f, 0.82f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.68f, 0.25f), p(0.80f, 0.80f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.18f, 0.82f), p(0.84f, 0.82f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.Player, UiIcon.Playback -> {
            val path = Path().apply {
                moveTo(w * 0.32f, h * 0.22f)
                lineTo(w * 0.32f, h * 0.78f)
                lineTo(w * 0.78f, h * 0.50f)
                close()
            }
            drawPath(path, tint)
        }
        UiIcon.Inbox -> {
            drawLine(tint, p(0.18f, 0.28f), p(0.82f, 0.28f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.18f, 0.28f), p(0.18f, 0.78f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.82f, 0.28f), p(0.82f, 0.78f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.18f, 0.78f), p(0.82f, 0.78f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.34f, 0.58f), p(0.46f, 0.70f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.46f, 0.70f), p(0.66f, 0.46f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.Settings -> {
            drawCircle(tint, radius = size.minDimension * 0.18f, center = p(0.50f, 0.50f), style = stroke)
            listOf(0.18f to 0.50f, 0.82f to 0.50f, 0.50f to 0.18f, 0.50f to 0.82f, 0.28f to 0.28f, 0.72f to 0.72f).forEach { (x, y) ->
                drawLine(tint, p(0.50f, 0.50f), p(x, y), strokeWidth * 0.75f, cap = StrokeCap.Round)
            }
        }
        UiIcon.NewMusic, UiIcon.Track -> {
            drawLine(tint, p(0.62f, 0.18f), p(0.62f, 0.66f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.62f, 0.18f), p(0.78f, 0.28f), strokeWidth, cap = StrokeCap.Round)
            drawCircle(tint, radius = size.minDimension * 0.14f, center = p(0.42f, 0.72f), style = stroke)
            drawLine(tint, p(0.42f, 0.72f), p(0.62f, 0.66f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.Health -> {
            drawLine(tint, p(0.18f, 0.58f), p(0.34f, 0.58f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.34f, 0.58f), p(0.44f, 0.34f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.44f, 0.34f), p(0.56f, 0.74f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.56f, 0.74f), p(0.66f, 0.50f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.66f, 0.50f), p(0.84f, 0.50f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.Duplicate -> {
            drawRect(tint, topLeft = p(0.30f, 0.22f), size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.44f), style = stroke)
            drawRect(tint, topLeft = p(0.18f, 0.34f), size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.44f), style = stroke)
        }
        UiIcon.Mixtape -> {
            drawRect(tint, topLeft = p(0.16f, 0.28f), size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.44f), style = stroke)
            drawCircle(tint, radius = size.minDimension * 0.10f, center = p(0.36f, 0.50f), style = stroke)
            drawCircle(tint, radius = size.minDimension * 0.10f, center = p(0.64f, 0.50f), style = stroke)
            drawLine(tint, p(0.34f, 0.68f), p(0.66f, 0.68f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.Queue -> {
            drawLine(tint, p(0.20f, 0.28f), p(0.72f, 0.28f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.20f, 0.50f), p(0.72f, 0.50f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.20f, 0.72f), p(0.56f, 0.72f), strokeWidth, cap = StrokeCap.Round)
            drawCircle(tint, radius = size.minDimension * 0.08f, center = p(0.82f, 0.72f), style = stroke)
        }
        UiIcon.Pipeline -> {
            drawCircle(tint, radius = size.minDimension * 0.10f, center = p(0.25f, 0.50f), style = stroke)
            drawCircle(tint, radius = size.minDimension * 0.10f, center = p(0.50f, 0.50f), style = stroke)
            drawCircle(tint, radius = size.minDimension * 0.10f, center = p(0.75f, 0.50f), style = stroke)
            drawLine(tint, p(0.35f, 0.50f), p(0.40f, 0.50f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.60f, 0.50f), p(0.65f, 0.50f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.Review, UiIcon.BlacklistOn -> {
            drawLine(tint, p(0.22f, 0.54f), p(0.42f, 0.72f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.42f, 0.72f), p(0.78f, 0.30f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.Root -> {
            drawLine(tint, p(0.16f, 0.34f), p(0.42f, 0.34f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.42f, 0.34f), p(0.50f, 0.44f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.50f, 0.44f), p(0.84f, 0.44f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.16f, 0.34f), p(0.16f, 0.78f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.84f, 0.44f), p(0.84f, 0.78f), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, p(0.16f, 0.78f), p(0.84f, 0.78f), strokeWidth, cap = StrokeCap.Round)
        }
        UiIcon.BlacklistOff -> {
            drawCircle(tint, radius = size.minDimension * 0.30f, center = p(0.50f, 0.50f), style = stroke)
            drawLine(tint, p(0.28f, 0.72f), p(0.72f, 0.28f), strokeWidth, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun WaveformMark() {
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 28.dp)) {
        val stroke = 8.dp.toPx()
        val step = size.width / 11f
        for (index in 0..10) {
            val centerX = index * step
            val heightFactor = listOf(0.28f, 0.52f, 0.36f, 0.86f, 0.62f, 1f, 0.68f, 0.4f, 0.78f, 0.48f, 0.3f)[index]
            val lineHeight = size.height * heightFactor
            drawLine(
                color = Color.White.copy(alpha = 0.78f),
                start = Offset(centerX, (size.height - lineHeight) / 2f),
                end = Offset(centerX, (size.height + lineHeight) / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}

private fun formatRelativeTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "Never scanned"
    val elapsedMinutes = ((System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        elapsedMinutes < 1 -> "Scanned just now"
        elapsedMinutes < 60 -> "Scanned ${elapsedMinutes}m ago"
        elapsedMinutes < 1_440 -> "Scanned ${elapsedMinutes / 60}h ago"
        else -> "Scanned ${elapsedMinutes / 1_440}d ago"
    }
}

private data class MusicRootSelection(
    val displayName: String,
    val uri: String,
    val location: String
)

private fun Uri.toMusicRootSelection(): MusicRootSelection {
    val rawPath = lastPathSegment.orEmpty()
        .substringAfter(':', missingDelimiterValue = lastPathSegment.orEmpty())
        .trim('/')
    val displayName = rawPath.substringAfterLast('/').ifBlank { "Music root" }
    val location = rawPath.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
    return MusicRootSelection(
        displayName = displayName,
        uri = toString(),
        location = location
    )
}

private val LibrarySort.label: String
    get() = when (this) {
        LibrarySort.Title -> "Title"
        LibrarySort.Artist -> "Artist"
        LibrarySort.Album -> "Album"
        LibrarySort.RecentlyAdded -> "Added"
        LibrarySort.Duration -> "Duration"
        LibrarySort.Size -> "Size"
    }

private object AudOneOutColors {
    val background = Color(0xFF101113)
    val surface = Color(0xFF191A1F)
    val surfaceAlt = Color(0xFF22232A)
    val line = Color(0xFF32333B)
    val textPrimary = Color(0xFFF6F1F4)
    val textSecondary = Color(0xFFD8CDD3)
    val textMuted = Color(0xFFA99DA5)
    val accentPurple = Color(0xFF7E5CFF)
    val accentCoral = Color(0xFFFF6B5F)
}

@Composable
private fun AudOneOutTheme(content: @Composable () -> Unit) {
    val colorScheme = androidx.compose.material3.darkColorScheme(
        primary = AudOneOutColors.accentCoral,
        secondary = AudOneOutColors.accentPurple,
        background = AudOneOutColors.background,
        surface = AudOneOutColors.surface,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = AudOneOutColors.textPrimary,
        onSurface = AudOneOutColors.textPrimary
    )

    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = AudOneOutColors.background) {
            content()
        }
    }
}
