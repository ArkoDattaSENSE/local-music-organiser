package com.audoneout.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.audoneout.app.scan.MediaStoreChangeObserver
import com.audoneout.app.ui.AudColors
import com.audoneout.app.ui.AudOneOutTheme
import com.audoneout.app.ui.DiscoverScreen
import com.audoneout.app.ui.DoctorScreen
import com.audoneout.app.ui.HomeScreen
import com.audoneout.app.ui.InboxScreen
import com.audoneout.app.ui.LibraryScreen
import com.audoneout.app.ui.MixtapeScreen
import com.audoneout.app.ui.PlaylistDetailScreen
import com.audoneout.app.ui.RadioScreen
import com.audoneout.app.ui.SettingsScreen
import com.audoneout.app.ui.TrackCallbacks
import com.audoneout.app.ui.TrackDetailScreen
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val DISCOVER = "discover"
    const val RADIO = "radio"
    const val SETTINGS = "settings"
    const val DOCTOR = "doctor"
    const val INBOX = "inbox"
    const val MIXTAPE = "mixtape"
    const val TRACK = "track/{trackId}"
    const val PLAYLIST = "playlist/{playlistId}"

    fun track(id: Long) = "track/$id"
    fun playlist(id: Long) = "playlist/$id"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Home", Icons.Rounded.Home),
    BottomDestination(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    BottomDestination(Routes.DISCOVER, "Discover", Icons.Rounded.Explore),
    BottomDestination(Routes.RADIO, "Radio", Icons.Rounded.Radio),
    BottomDestination(Routes.SETTINGS, "Settings", Icons.Rounded.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudOneOutApp(viewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val isRootDestination = bottomDestinations.any { destination ->
        backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
    }
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 33 && grants[Manifest.permission.POST_NOTIFICATIONS] == false) {
            viewModel.setNotifyWhenNewTracksReady(false)
        }
        if (grants[audioPermission] == true || ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanLibrary()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotifyWhenNewTracksReady(granted)
    }
    val metadataWritePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.completeMetadataWritePermission(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel) {
        viewModel.metadataWritePermissionRequests.collect { intentSender ->
            metadataWritePermissionLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }
    val requestScan = {
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanLibrary()
        } else {
            val permissions = buildList {
                add(audioPermission)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
    val setNotificationsEnabled: (Boolean) -> Unit = { enabled ->
        if (
            enabled &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.setNotifyWhenNewTracksReady(false)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotifyWhenNewTracksReady(enabled)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }.orEmpty()
                }
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported playlist.m3u8"
                viewModel.importPlaylist(fileName, text)
            }
        }
    }

    var pendingExport by remember { mutableStateOf<ExportPayload?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val payload = pendingExport
        if (uri != null && payload != null) {
            scope.launch {
                val successful = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload.text) }
                            ?: error("Could not open the export destination")
                    }.isSuccess
                }
                viewModel.completeExport(successful)
            }
        } else {
            viewModel.completeExport(false)
        }
        pendingExport = null
    }

    LaunchedEffect(state.exportPayload?.token) {
        state.exportPayload?.let { payload ->
            pendingExport = payload
            exportLauncher.launch(payload.fileName)
        }
    }
    LaunchedEffect(state.statusMessage) {
        if (state.statusMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(state.statusMessage)
            viewModel.clearMessage()
        }
    }

    state.soundiizGuide?.let { guide ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSoundiizGuide,
            title = { Text("Move this playlist to streaming") },
            text = { Text(guide) },
            confirmButton = {
                Button(onClick = viewModel::dismissSoundiizGuide) { Text("Done") }
            }
        )
    }

    val trackCallbacks = TrackCallbacks(
        onOpen = viewModel::openTrack,
        onToggleFavorite = viewModel::toggleFavorite,
        onStartRadio = { trackId ->
            viewModel.startTrackRadio(trackId)
            if (currentRoute != Routes.RADIO) {
                navController.navigate(Routes.RADIO) { launchSingleTop = true }
            }
        },
        onAddToPlaylist = viewModel::addTrackToPlaylist,
        onDetails = { navController.navigate(Routes.track(it)) }
    )

    Scaffold(
        containerColor = AudColors.Ink,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentRoute == Routes.HOME && state.hasLibrary) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.MIXTAPE) },
                    icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                    text = { Text("Create Mixtape") },
                    containerColor = AudColors.Coral
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(routeTitle(currentRoute)) },
                navigationIcon = {
                    if (!isRootDestination) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AudColors.Ink)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = AudColors.Surface) {
                bottomDestinations.forEach { destination ->
                    val selected = backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(contentPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    state = state,
                    trackCallbacks = trackCallbacks,
                    onSetUpLibrary = { navController.navigate(Routes.SETTINGS) },
                    onScan = requestScan,
                    onCancelScan = viewModel::cancelLibraryScan,
                    onOpenDoctor = { navController.navigate(Routes.DOCTOR) },
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                    onOpenDiscover = { navController.navigate(Routes.DISCOVER) }
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    state = state,
                    trackCallbacks = trackCallbacks,
                    onViewChange = viewModel::setLibraryView,
                    onSortChange = viewModel::setLibrarySort,
                    onQueryChange = viewModel::setLibraryQuery,
                    onToggleGrid = viewModel::toggleLibraryGridMode,
                    onOpenAlbum = viewModel::openAlbum,
                    onOpenArtist = viewModel::openArtist,
                    onOpenFolder = viewModel::openFolder,
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    onCreatePlaylist = viewModel::createPlaylist,
                    onImportPlaylist = { importLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/csv", "text/plain")) }
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen(
                    state = state,
                    callbacks = trackCallbacks,
                    onRefreshLocal = viewModel::refreshRecommendations,
                    onOpenMix = viewModel::openRecommendations,
                    onOpenMixtape = { navController.navigate(Routes.MIXTAPE) },
                    onRefreshLastFm = viewModel::refreshLastFmRecommendations,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenLocalMatch = viewModel::openLastFmLocalMatch,
                    onOpenLastFmPage = viewModel::openLastFmPage,
                    onOpenYoutubeMusic = viewModel::openYoutubeMusic
                )
            }
            composable(Routes.RADIO) {
                RadioScreen(
                    state = state,
                    callbacks = trackCallbacks,
                    onStartPlaylistRadio = viewModel::startPlaylistRadio,
                    onSaveStation = viewModel::saveRadioStation,
                    onOpenStation = viewModel::openRadioStation,
                    onDeleteStation = viewModel::deleteRadioStation
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = state,
                    onScan = requestScan,
                    onCancelScan = viewModel::cancelLibraryScan,
                    onAddRoot = viewModel::addRoot,
                    onRootIncludedChange = viewModel::setRootIncluded,
                    onRescanRoot = viewModel::rescanRoot,
                    onRemoveRoot = viewModel::removeRoot,
                    onAddRule = viewModel::addBlacklistRule,
                    onAddBlacklistPath = viewModel::addBlacklistPath,
                    onRuleEnabledChange = viewModel::setBlacklistRuleEnabled,
                    onDeleteRule = viewModel::deleteBlacklistRule,
                    onRestoreRules = viewModel::restoreDefaultBlacklist,
                    onAutomaticChecksChange = viewModel::setAutomaticLibraryChecking,
                    onCheckFrequencyChange = viewModel::setCheckFrequency,
                    onWifiOnlyChange = viewModel::setWifiOnlyOnlineEnrichment,
                    onAutoEnhanceChange = viewModel::setEnhanceNewTracksAutomatically,
                    onChargingOnlyChange = viewModel::setAnalyseOnlyWhileCharging,
                    onNotificationsChange = setNotificationsEnabled,
                    onQuietModeChange = viewModel::setQuietBackgroundMode,
                    onOnlineEnrichmentChange = viewModel::setOnlineEnrichmentEnabled,
                    onLastFmEnabledChange = viewModel::setLastFmEnabled,
                    onSaveLastFm = viewModel::saveLastFmCredentials,
                    onOpenLastFmApiSetup = viewModel::openLastFmApiSetup
                )
            }
            composable(Routes.DOCTOR) {
                DoctorScreen(state, viewModel::runHealthCheck, { navController.navigate(Routes.track(it)) }, viewModel::ignoreFinding)
            }
            composable(Routes.INBOX) {
                InboxScreen(
                    state = state,
                    callbacks = trackCallbacks,
                    onRunDoctor = viewModel::runHealthCheck,
                    onAcceptSafeSuggestions = viewModel::acceptSafeMetadataSuggestions,
                    onOpenDoctor = { navController.navigate(Routes.DOCTOR) }
                )
            }
            composable(Routes.MIXTAPE) {
                MixtapeScreen(state, viewModel::setMixtapePrompt, viewModel::generateMixtape, viewModel::openMixtape, viewModel::saveMixtape)
            }
            composable(Routes.TRACK, arguments = listOf(navArgument("trackId") { type = NavType.LongType })) { entry ->
                TrackDetailScreen(
                    trackId = entry.arguments?.getLong("trackId") ?: 0,
                    state = state,
                    onOpen = viewModel::openTrack,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onStartRadio = {
                        viewModel.startTrackRadio(it)
                        navController.navigate(Routes.RADIO) { launchSingleTop = true }
                    },
                    onLoadEnrichment = viewModel::loadTrackEnrichment,
                    onFindMetadata = viewModel::findTrackMetadata,
                    onAcceptSuggestion = viewModel::acceptMetadataSuggestion,
                    onIgnoreSuggestion = viewModel::ignoreMetadataSuggestion,
                    onSaveMetadata = viewModel::saveTrackMetadata,
                    onWriteMetadata = viewModel::requestMetadataWriteback
                )
            }
            composable(Routes.PLAYLIST, arguments = listOf(navArgument("playlistId") { type = NavType.LongType })) { entry ->
                val playlistId = entry.arguments?.getLong("playlistId") ?: 0
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    state = state,
                    callbacks = trackCallbacks,
                    onLoad = viewModel::loadPlaylist,
                    onOpen = { viewModel.openPlaylist(it) },
                    onStartRadio = { viewModel.startPlaylistRadio(it); navController.navigate(Routes.RADIO) },
                    onExportM3u = { viewModel.preparePlaylistExport(it, "m3u8") },
                    onExportCsv = { viewModel.preparePlaylistExport(it, "csv") },
                    onDelete = { viewModel.deletePlaylist(it); navController.popBackStack() },
                    onRemoveTrack = viewModel::removeTrackFromPlaylist,
                    onMoveTrack = viewModel::movePlaylistTrack
                )
            }
        }
    }
}

private fun routeTitle(route: String): String = when (route) {
    Routes.HOME -> "AudOneOut"
    Routes.LIBRARY -> "Library"
    Routes.DISCOVER -> "Discover"
    Routes.RADIO -> "Radio"
    Routes.SETTINGS -> "Settings"
    Routes.DOCTOR -> "Library Doctor"
    Routes.INBOX -> "New Music"
    Routes.MIXTAPE -> "Create Mixtape"
    Routes.TRACK -> "Track Details"
    Routes.PLAYLIST -> "Playlist"
    else -> "AudOneOut"
}
