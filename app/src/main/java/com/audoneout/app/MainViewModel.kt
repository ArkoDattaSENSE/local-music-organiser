package com.audoneout.app

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audoneout.app.data.AlbumEntity
import com.audoneout.app.data.AnalysisResultEntity
import com.audoneout.app.data.ArtistEntity
import com.audoneout.app.data.EnrichedMetadataEntity
import com.audoneout.app.data.FolderBlacklistRuleEntity
import com.audoneout.app.data.FolderEntity
import com.audoneout.app.data.LibraryRepository
import com.audoneout.app.data.ListeningEventEntity
import com.audoneout.app.data.MetadataSuggestionEntity
import com.audoneout.app.data.MetadataWritebackEntity
import com.audoneout.app.data.MusicRootEntity
import com.audoneout.app.data.OriginalMetadataEntity
import com.audoneout.app.data.PlaylistEntity
import com.audoneout.app.data.RadioStationEntity
import com.audoneout.app.data.ScanJobEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.data.UserConfirmedMetadataEntity
import com.audoneout.app.domain.LibraryHealth
import com.audoneout.app.domain.PlaylistCandidate
import com.audoneout.app.domain.PlaylistRules
import com.audoneout.app.domain.ScanProgress
import com.audoneout.app.handoff.ExternalPlayerLauncher
import com.audoneout.app.lastfm.LastFmDiscoveryResult
import com.audoneout.app.lastfm.LastFmClient
import com.audoneout.app.lastfm.LastFmLinks
import com.audoneout.app.lastfm.LastFmProfile
import com.audoneout.app.lastfm.LastFmRecommendation
import com.audoneout.app.lastfm.LastFmRecommendationService
import com.audoneout.app.metadata.MetadataWritebackManager
import com.audoneout.app.metadata.TrackMetadataInput
import com.audoneout.app.playlist.LibrarySummary
import com.audoneout.app.playlist.LocalRuleBasedPromptInterpreter
import com.audoneout.app.playlist.PlaylistGenerator
import com.audoneout.app.playlist.PlaylistExporter
import com.audoneout.app.playlist.PlaylistImporter
import com.audoneout.app.recommendation.OfflineRecommender
import com.audoneout.app.recommendation.TrackRecommendation
import com.audoneout.app.radio.RadioStreamResolver
import com.audoneout.app.settings.AppSettings
import com.audoneout.app.settings.NetworkPolicy
import com.audoneout.app.settings.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibrarySnapshot(
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val folderCount: Int = 0
)

data class MainUiState(
    val library: LibrarySnapshot = LibrarySnapshot(),
    val tracks: List<TrackEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val inbox: List<TrackEntity> = emptyList(),
    val analysisResults: List<AnalysisResultEntity> = emptyList(),
    val metadataSuggestions: List<MetadataSuggestionEntity> = emptyList(),
    val selectedTrackEnrichmentId: Long? = null,
    val selectedTrackEnrichment: List<EnrichedMetadataEntity> = emptyList(),
    val selectedTrackOriginalMetadata: OriginalMetadataEntity? = null,
    val userConfirmedMetadata: List<UserConfirmedMetadataEntity> = emptyList(),
    val metadataWritebacks: List<MetadataWritebackEntity> = emptyList(),
    val roots: List<MusicRootEntity> = emptyList(),
    val blacklistRules: List<FolderBlacklistRuleEntity> = emptyList(),
    val favorites: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val radioStations: List<RadioStationEntity> = emptyList(),
    val selectedPlaylistId: Long? = null,
    val selectedPlaylistTracks: List<TrackEntity> = emptyList(),
    val selectedPlaylistUnavailableCount: Int = 0,
    val libraryView: LibraryView = LibraryView.Songs,
    val librarySort: LibrarySort = LibrarySort.Title,
    val libraryQuery: String = "",
    val libraryGridMode: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(estimatedRemainingWork = "Ready"),
    val lastCompletedScan: ScanJobEntity? = null,
    val settings: SettingsState = SettingsState(),
    val health: LibraryHealth = LibraryHealth(100, 0, 0, 0),
    val recommendations: List<TrackRecommendation> = emptyList(),
    val lastFmProfile: LastFmProfile? = null,
    val lastFmRecommendations: List<LastFmRecommendation> = emptyList(),
    val localLastFmTasteSeedCount: Int = 0,
    val recentLastFmScrobbleCount: Int = 0,
    val lastFmLoading: Boolean = false,
    val lastFmError: String = "",
    val metadataLookupTrackId: Long? = null,
    val metadataWritebackTrackId: Long? = null,
    val localRadio: List<TrackRecommendation> = emptyList(),
    val radioSeedLabel: String = "",
    val mixtapePrompt: String = "Make a rainy Kolkata evening mixtape without repeating artists",
    val mixtapeRules: PlaylistRules? = null,
    val mixtapeCandidates: List<PlaylistCandidate> = emptyList(),
    val mixtapeMessage: String = "Describe the mood, duration, folders, languages, genres, or formats.",
    val statusMessage: String = "",
    val exportPayload: ExportPayload? = null,
    val soundiizGuide: String? = null,
    val busy: Boolean = false,
    val scanRunning: Boolean = false
) {
    val favoriteIds: Set<Long> get() = favorites.mapTo(mutableSetOf()) { it.id }
    val hasLibrary: Boolean get() = library.trackCount > 0
    fun confirmedMetadataFor(trackId: Long): UserConfirmedMetadataEntity? =
        userConfirmedMetadata.firstOrNull { it.trackId == trackId }
    fun writebackFor(trackId: Long): MetadataWritebackEntity? =
        metadataWritebacks.firstOrNull { it.trackId == trackId }
}

data class ExportPayload(
    val token: Long = System.currentTimeMillis(),
    val fileName: String,
    val mimeType: String,
    val text: String
)

enum class LibraryView { Songs, Albums, Artists, Folders, Playlists }

enum class LibrarySort(val label: String) {
    Title("Title"),
    Artist("Artist"),
    Album("Album"),
    RecentlyAdded("Recent"),
    Duration("Duration"),
    Size("Size")
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val settings: AppSettings,
    private val promptInterpreter: LocalRuleBasedPromptInterpreter,
    private val playlistGenerator: PlaylistGenerator,
    private val playlistImporter: PlaylistImporter,
    private val playlistExporter: PlaylistExporter,
    private val recommender: OfflineRecommender,
    private val lastFmClient: LastFmClient,
    private val lastFmRecommendationService: LastFmRecommendationService,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
    private val radioStreamResolver: RadioStreamResolver,
    private val metadataWritebackManager: MetadataWritebackManager,
    private val networkPolicy: NetworkPolicy
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val _metadataWritePermissionRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val metadataWritePermissionRequests: SharedFlow<IntentSender> = _metadataWritePermissionRequests.asSharedFlow()

    private var allTracks: List<TrackEntity> = emptyList()
    private var allAlbums: List<AlbumEntity> = emptyList()
    private var allArtists: List<ArtistEntity> = emptyList()
    private var allFolders: List<FolderEntity> = emptyList()
    private var manualScanJob: Job? = null
    private var pendingMetadataWritebackTrackId: Long? = null
    private var lastFmTasteSeeds: List<TrackEntity> = emptyList()
    private var recommendationRotation: Long = 0

    init {
        viewModelScope.launch { repository.ensureDefaultBlacklist() }
        viewModelScope.launch {
            repository.counts.collect { counts ->
                _uiState.update { it.copy(library = LibrarySnapshot(counts.tracks, counts.albums, counts.artists, counts.folders)) }
            }
        }
        viewModelScope.launch {
            repository.tracks.collect { tracks ->
                allTracks = tracks
                publishLibraryContent()
            }
        }
        viewModelScope.launch {
            repository.albums.collect { albums ->
                allAlbums = albums
                publishLibraryContent()
            }
        }
        viewModelScope.launch {
            repository.artists.collect { artists ->
                allArtists = artists
                publishLibraryContent()
            }
        }
        viewModelScope.launch {
            repository.folders.collect { folders ->
                allFolders = folders
                publishLibraryContent()
            }
        }
        viewModelScope.launch { repository.inbox.collect { value -> _uiState.update { it.copy(inbox = value) } } }
        viewModelScope.launch { repository.analysisResults.collect { value -> _uiState.update { it.copy(analysisResults = value) } } }
        viewModelScope.launch { repository.metadataSuggestions.collect { value -> _uiState.update { it.copy(metadataSuggestions = value) } } }
        viewModelScope.launch { repository.userConfirmedMetadata.collect { value -> _uiState.update { it.copy(userConfirmedMetadata = value) } } }
        viewModelScope.launch { repository.metadataWritebacks.collect { value -> _uiState.update { it.copy(metadataWritebacks = value) } } }
        viewModelScope.launch { repository.roots.collect { value -> _uiState.update { it.copy(roots = value) } } }
        viewModelScope.launch { repository.blacklistRules.collect { value -> _uiState.update { it.copy(blacklistRules = value) } } }
        viewModelScope.launch { repository.playlists.collect { value -> _uiState.update { it.copy(playlists = value) } } }
        viewModelScope.launch { repository.radioStations.collect { value -> _uiState.update { it.copy(radioStations = value) } } }
        viewModelScope.launch {
            repository.scanProgress.collect { value -> _uiState.update { it.copy(scanProgress = value) } }
        }
        viewModelScope.launch {
            repository.latestCompletedScan.collect { value -> _uiState.update { it.copy(lastCompletedScan = value) } }
        }
        viewModelScope.launch {
            var loadedConfiguration = ""
            settings.state.collect { value ->
                _uiState.update { it.copy(settings = value) }
                val configuration =
                    "${value.onlineEnrichmentEnabled}|${value.lastFmEnabled}|${value.lastFmUsername}|${value.lastFmApiKey}"
                if (
                    value.onlineEnrichmentEnabled &&
                    value.lastFmEnabled &&
                    value.lastFmUsername.isNotBlank() &&
                    value.lastFmApiKey.isNotBlank() &&
                    configuration != loadedConfiguration
                ) {
                    loadedConfiguration = configuration
                    refreshLastFmRecommendations()
                }
            }
        }
        viewModelScope.launch {
            combine(repository.tracks, repository.favoriteTracks) { tracks, favorites -> tracks to favorites }
                .collect { (tracks, favorites) ->
                    _uiState.update { it.copy(favorites = favorites) }
                    val events = repository.listeningEventsOnce()
                    val recommendations = buildLocalRecommendations(tracks, favorites, events)
                    _uiState.update { it.copy(recommendations = recommendations) }
                }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(health = repository.currentHealth()) }
        }
    }

    fun scanLibrary() {
        if (manualScanJob?.isActive == true) return
        manualScanJob = viewModelScope.launch {
            _uiState.update { it.copy(busy = true, scanRunning = true) }
            try {
                repository.scanMediaStore()
                _uiState.update { it.copy(health = repository.currentHealth()) }
                setMessage("Library scan complete")
            } catch (_: CancellationException) {
                repository.markScanPaused()
                setMessage("Scan paused. Run it again to resume from the current catalog.")
            } catch (error: Throwable) {
                setMessage(error.message ?: "Library scan failed")
            } finally {
                _uiState.update { it.copy(busy = false, scanRunning = false) }
            }
        }
    }

    fun cancelLibraryScan() {
        manualScanJob?.cancel()
    }

    fun addRoot(displayName: String, uri: String, location: String = uri) {
        viewModelScope.launch {
            repository.addMusicRoot(displayName, uri, location)
            setMessage("$displayName added. Tap Scan included folders when you are ready.")
        }
    }

    fun setRootIncluded(rootId: Long, included: Boolean) {
        viewModelScope.launch { repository.setMusicRootIncluded(rootId, included) }
    }

    fun rescanRoot(rootId: Long) = launchBusy("Folder scan complete") {
        repository.scanMusicRoot(rootId)
        _uiState.update { it.copy(health = repository.currentHealth()) }
    }

    fun removeRoot(rootId: Long) {
        viewModelScope.launch { repository.removeMusicRoot(rootId) }
    }

    fun addBlacklistRule(name: String) {
        viewModelScope.launch { repository.addFolderNameRule(name) }
    }

    fun addBlacklistPath(label: String, path: String) {
        viewModelScope.launch { repository.addFolderPathRule(label, path) }
    }

    fun setBlacklistRuleEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setBlacklistRuleEnabled(ruleId, enabled) }
    }

    fun deleteBlacklistRule(ruleId: Long) {
        viewModelScope.launch { repository.deleteBlacklistRule(ruleId) }
    }

    fun restoreDefaultBlacklist() {
        viewModelScope.launch { repository.restoreDefaultBlacklist() }
    }

    fun runHealthCheck() = launchBusy("Library Doctor finished") {
        _uiState.update { it.copy(health = repository.runLibraryDoctor()) }
    }

    fun ignoreFinding(resultId: Long) {
        viewModelScope.launch { repository.ignoreAnalysisResult(resultId) }
    }

    fun setLibraryView(view: LibraryView) {
        _uiState.update { it.copy(libraryView = view) }
        publishLibraryContent()
    }

    fun setLibrarySort(sort: LibrarySort) {
        _uiState.update { it.copy(librarySort = sort) }
        publishLibraryContent()
    }

    fun setLibraryQuery(query: String) {
        _uiState.update { it.copy(libraryQuery = query) }
        publishLibraryContent()
    }

    fun toggleLibraryGridMode() {
        _uiState.update { it.copy(libraryGridMode = !it.libraryGridMode) }
    }

    fun openTrack(trackId: Long) {
        val track = allTracks.firstOrNull { it.id == trackId } ?: return
        externalPlayerLauncher.openTrack(track).onFailure { setMessage(it.message ?: "No music player could open this track") }
    }

    fun openTracks(name: String, trackIds: List<Long>) {
        viewModelScope.launch {
            val wanted = trackIds.toSet()
            val tracks = repository.availableTracksOnce().filter { it.id in wanted }
            externalPlayerLauncher.openPlaylist(name, tracks)
                .onFailure { setMessage(it.message ?: "No music player could open this playlist") }
        }
    }

    fun openAlbum(albumKey: String) {
        val album = allAlbums.firstOrNull { it.albumKey == albumKey } ?: return
        val tracks = allTracks.filter { it.album == album.title && it.artist == album.artistName }
        externalPlayerLauncher.openPlaylist(album.title, tracks)
            .onFailure { setMessage(it.message ?: "No music player could open this album") }
    }

    fun openArtist(artistKey: String) {
        val artist = allArtists.firstOrNull { it.artistKey == artistKey } ?: return
        val tracks = allTracks.filter { it.artist == artist.name }
        externalPlayerLauncher.openPlaylist(artist.name, tracks)
            .onFailure { setMessage(it.message ?: "No music player could open this artist mix") }
    }

    fun openFolder(path: String) {
        val tracks = allTracks.filter { it.relativePath == path || it.relativePath.startsWith(path) }
        externalPlayerLauncher.openPlaylist(path.substringAfterLast('/').ifBlank { "Folder mix" }, tracks)
            .onFailure { setMessage(it.message ?: "No music player could open this folder") }
    }

    fun openPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val tracks = repository.playlistTracksOnce(playlistId)
            val name = _uiState.value.playlists.firstOrNull { it.id == playlistId }?.name ?: "AudOneOut playlist"
            externalPlayerLauncher.openPlaylist(name, tracks)
                .onFailure { setMessage(it.message ?: "No music player could open this playlist") }
        }
    }

    fun loadPlaylist(playlistId: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedPlaylistId = playlistId,
                    selectedPlaylistTracks = repository.playlistTracksOnce(playlistId),
                    selectedPlaylistUnavailableCount = repository.unavailablePlaylistTrackCount(playlistId)
                )
            }
        }
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch { repository.toggleFavorite(trackId) }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
            setMessage("Added to playlist")
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.savePlaylist(name.trim(), "Manual playlist", emptyList())
            setMessage("Playlist created")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
            loadPlaylist(playlistId)
        }
    }

    fun movePlaylistTrack(playlistId: Long, trackId: Long, direction: Int) {
        viewModelScope.launch {
            repository.movePlaylistTrack(playlistId, trackId, direction)
            loadPlaylist(playlistId)
        }
    }

    fun importPlaylist(fileName: String, text: String) {
        viewModelScope.launch {
            val baseName = fileName.substringBeforeLast('.').ifBlank { "Imported playlist" }
            val imported = if (fileName.endsWith(".csv", true)) {
                playlistImporter.fromCsv(baseName, text)
            } else {
                playlistImporter.fromM3u(baseName, text)
            }
            val library = repository.availableTracksOnce()
            val matched = imported.entries.mapNotNull { entry ->
                library.firstOrNull { it.contentUri == entry.reference } ?:
                    library.firstOrNull { entry.reference.isNotBlank() && entry.reference.endsWith(it.fileName, true) } ?:
                    library.firstOrNull {
                        entry.title.isNotBlank() && it.title.equals(entry.title, true) &&
                            (entry.artist.isBlank() || it.artist.equals(entry.artist, true))
                    }
            }.distinctBy { it.id }
            repository.savePlaylist(
                name = imported.name,
                description = "Imported from $fileName; ${imported.entries.size - matched.size} unmatched entries",
                trackIds = matched.map { it.id }
            )
            setMessage("Imported ${matched.size} tracks; ${imported.entries.size - matched.size} need manual matching")
        }
    }

    fun preparePlaylistExport(playlistId: Long, format: String) {
        viewModelScope.launch {
            val playlist = _uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return@launch
            val tracks = repository.playlistTracksOnce(playlistId)
            val safeName = playlist.name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
            val payload = if (format.equals("csv", true)) {
                ExportPayload(fileName = "$safeName.csv", mimeType = "text/csv", text = playlistExporter.toMigrationCsv(tracks))
            } else {
                ExportPayload(fileName = "$safeName.m3u8", mimeType = "audio/x-mpegurl", text = playlistExporter.toM3u8(playlist.name, tracks))
            }
            _uiState.update { it.copy(exportPayload = payload) }
        }
    }

    fun completeExport(successful: Boolean) {
        val payload = _uiState.value.exportPayload
        _uiState.update {
            it.copy(
                exportPayload = null,
                soundiizGuide = if (successful && payload?.mimeType == "text/csv") {
                    playlistExporter.soundiizMigrationGuide(payload.fileName)
                } else null
            )
        }
        if (successful) setMessage("Playlist exported")
    }

    fun dismissSoundiizGuide() {
        _uiState.update { it.copy(soundiizGuide = null) }
    }

    fun startTrackRadio(trackId: Long) {
        val seed = allTracks.firstOrNull { it.id == trackId } ?: return
        startLocalRadio(listOf(seed), seed.title)
    }

    fun startPlaylistRadio(playlistId: Long) {
        viewModelScope.launch {
            val seeds = repository.playlistTracksOnce(playlistId)
            val name = _uiState.value.playlists.firstOrNull { it.id == playlistId }?.name ?: "Playlist"
            generateAndOpenRadio(seeds, name)
        }
    }

    fun refreshRecommendations() {
        viewModelScope.launch {
            recommendationRotation += 1
            val tracks = repository.availableTracksOnce()
            val favorites = repository.favoriteTracksOnce()
            val events = repository.listeningEventsOnce()
            _uiState.update { it.copy(recommendations = buildLocalRecommendations(tracks, favorites, events)) }
        }
    }

    fun saveLastFmCredentials(username: String, apiKey: String) {
        if (username.isBlank() || apiKey.isBlank()) {
            setMessage("Enter both a Last.fm username and API key")
            return
        }
        viewModelScope.launch {
            settings.setOnlineEnrichmentEnabled(true)
            settings.setLastFmCredentials(username, apiKey)
            setMessage("Last.fm profile saved")
        }
    }

    fun setLastFmEnabled(enabled: Boolean) {
        val current = _uiState.value.settings
        if (enabled && (current.lastFmUsername.isBlank() || current.lastFmApiKey.isBlank())) {
            setMessage("Save your Last.fm username and API key first")
            return
        }
        viewModelScope.launch {
            if (enabled) settings.setOnlineEnrichmentEnabled(true)
            settings.setLastFmEnabled(enabled)
        }
    }

    fun refreshLastFmRecommendations() {
        val configuration = _uiState.value.settings
        if (!configuration.onlineEnrichmentEnabled || !configuration.lastFmEnabled) {
            _uiState.update { it.copy(lastFmError = "Enable online enrichment and Last.fm in Settings first") }
            return
        }
        if (configuration.lastFmUsername.isBlank() || configuration.lastFmApiKey.isBlank()) {
            _uiState.update { it.copy(lastFmError = "Set up Last.fm in Settings first") }
            return
        }
        if (!networkPolicy.canUseOnlineEnrichment(configuration.wifiOnlyOnlineEnrichment)) {
            _uiState.update {
                it.copy(
                    lastFmLoading = false,
                    lastFmError = if (configuration.wifiOnlyOnlineEnrichment) {
                        "Connect to Wi-Fi to refresh Last.fm, or disable Wi-Fi only in Settings"
                    } else {
                        "Connect to the internet to refresh Last.fm"
                    }
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(lastFmLoading = true, lastFmError = "") }
            runCatching {
                lastFmRecommendationService.discover(
                    username = configuration.lastFmUsername,
                    apiKey = configuration.lastFmApiKey,
                    localTracks = repository.availableTracksOnce()
                )
            }.onSuccess(::publishLastFmDiscovery)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            lastFmLoading = false,
                            lastFmError = error.message ?: "Last.fm recommendations could not be refreshed"
                        )
                    }
                }
        }
    }

    fun openLastFmLocalMatch(recommendation: LastFmRecommendation) {
        val local = recommendation.localTrack
        if (local == null) {
            setMessage("This recommendation is not in your local library")
            return
        }
        externalPlayerLauncher.openTrack(local)
            .onFailure { setMessage(it.message ?: "No music player could open this track") }
    }

    fun openLastFmPage(url: String) {
        if (url.isBlank()) return
        externalPlayerLauncher.openWeb(url, "Open on Last.fm")
            .onFailure { setMessage(it.message ?: "No browser could open Last.fm") }
    }

    fun openYoutubeMusic(recommendation: LastFmRecommendation) {
        externalPlayerLauncher.openWeb(
            LastFmLinks.youtubeMusicSearch(recommendation.track.artist, recommendation.track.name),
            "Search YouTube Music"
        ).onFailure { setMessage(it.message ?: "No browser could open YouTube Music") }
    }

    fun openLastFmApiSetup() {
        externalPlayerLauncher.openWeb(LastFmLinks.apiAccountUrl, "Create a Last.fm API account")
            .onFailure { setMessage(it.message ?: "No browser could open Last.fm") }
    }

    fun findTrackMetadata(trackId: Long) {
        val track = allTracks.firstOrNull { it.id == trackId }
        if (track == null) {
            setMessage("Track is no longer available")
            return
        }
        val configuration = _uiState.value.settings
        if (!configuration.onlineEnrichmentEnabled || !configuration.lastFmEnabled) {
            setMessage("Enable online enrichment and Last.fm in Settings first")
            return
        }
        if (configuration.lastFmUsername.isBlank() || configuration.lastFmApiKey.isBlank()) {
            setMessage("Set up Last.fm in Settings first")
            return
        }
        if (track.title.isBlank() || track.artist.isBlank()) {
            setMessage("Add a title and artist before looking up this track")
            return
        }
        if (!networkPolicy.canUseOnlineEnrichment(configuration.wifiOnlyOnlineEnrichment)) {
            setMessage(
                if (configuration.wifiOnlyOnlineEnrichment) {
                    "Connect to Wi-Fi for metadata lookup, or disable Wi-Fi only in Settings"
                } else {
                    "Connect to the internet for metadata lookup"
                }
            )
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(metadataLookupTrackId = trackId) }
            runCatching {
                lastFmClient.getTrackInfo(
                    artist = track.artist,
                    track = track.title,
                    username = configuration.lastFmUsername,
                    apiKey = configuration.lastFmApiKey
                )
            }.onSuccess { info ->
                val count = repository.saveMetadataLookup(
                    trackId = trackId,
                    source = "Last.fm",
                    values = mapOf(
                        "title" to info.name,
                        "artist" to info.artist,
                        "album" to info.album,
                        "genre" to info.tags.firstOrNull().orEmpty(),
                        "tags" to info.tags.joinToString(", "),
                        "artworkUrl" to info.imageUrl
                    ),
                    externalId = info.mbid,
                    externalUrl = info.url
                )
                loadTrackEnrichment(trackId)
                setMessage(if (count == 0) "Last.fm metadata already matches" else "$count metadata suggestions ready to review")
            }.onFailure { error ->
                setMessage(error.message ?: "Last.fm metadata lookup failed")
            }
            _uiState.update { it.copy(metadataLookupTrackId = null) }
        }
    }

    fun loadTrackEnrichment(trackId: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedTrackEnrichmentId = trackId,
                    selectedTrackEnrichment = repository.enrichedMetadataOnce(trackId),
                    selectedTrackOriginalMetadata = repository.originalMetadataOnce(trackId)
                )
            }
        }
    }

    fun acceptMetadataSuggestion(suggestionId: Long) {
        viewModelScope.launch {
            val trackId = _uiState.value.metadataSuggestions.firstOrNull { it.id == suggestionId }?.trackId
            repository.acceptMetadataSuggestion(suggestionId)
            trackId?.let { loadTrackEnrichment(it) }
            setMessage("Accepted for discovery and recommendations. File writeback is ready.")
        }
    }

    fun ignoreMetadataSuggestion(suggestionId: Long) {
        viewModelScope.launch { repository.ignoreMetadataSuggestion(suggestionId) }
    }

    fun acceptSafeMetadataSuggestions() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            runCatching { repository.acceptSafeMetadataSuggestions() }
                .onSuccess { count ->
                    _uiState.update { it.copy(health = repository.currentHealth()) }
                    setMessage(
                        if (count == 0) "No high-confidence suggestions are waiting"
                        else "Accepted $count suggestions. File writeback remains per-track and explicit."
                    )
                }
                .onFailure { error -> setMessage(error.message ?: "Could not accept metadata suggestions") }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun saveTrackMetadata(input: TrackMetadataInput) {
        viewModelScope.launch {
            runCatching {
                repository.saveUserMetadata(input)
                _uiState.update { it.copy(health = repository.currentHealth()) }
            }.onSuccess {
                loadTrackEnrichment(input.trackId)
                setMessage("Metadata saved for discovery. Review the writeback card to update the file.")
            }.onFailure { error ->
                setMessage(error.message ?: "Could not save metadata")
            }
        }
    }

    fun requestMetadataWriteback(trackId: Long) {
        if (_uiState.value.metadataWritebackTrackId != null) return
        viewModelScope.launch {
            runCatching { metadataWritebackManager.permissionRequest(trackId) }
                .onSuccess { request ->
                    if (request == null) {
                        performMetadataWriteback(trackId)
                    } else {
                        pendingMetadataWritebackTrackId = trackId
                        _metadataWritePermissionRequests.emit(request)
                    }
                }
                .onFailure { error ->
                    setMessage(error.message ?: "This file cannot be opened for metadata writeback")
                }
        }
    }

    fun completeMetadataWritePermission(granted: Boolean) {
        val trackId = pendingMetadataWritebackTrackId
        pendingMetadataWritebackTrackId = null
        if (!granted || trackId == null) {
            setMessage("File writeback cancelled; AudOneOut metadata is unchanged")
            return
        }
        viewModelScope.launch { performMetadataWriteback(trackId) }
    }

    private suspend fun performMetadataWriteback(trackId: Long) {
        _uiState.update { it.copy(metadataWritebackTrackId = trackId) }
        runCatching { metadataWritebackManager.write(trackId) }
            .onSuccess { result ->
                val message = if (result.catalogOnlyFields.isEmpty()) {
                    "Verified ${result.writtenFields.size} tags in the audio file"
                } else {
                    "Wrote ${result.writtenFields.size} tags; ${result.catalogOnlyFields.joinToString()} remain catalog-only"
                }
                setMessage(message)
            }
            .onFailure { error ->
                setMessage(error.message ?: "Metadata writeback failed")
            }
        _uiState.update { it.copy(metadataWritebackTrackId = null) }
    }

    fun openRecommendations() {
        val tracks = _uiState.value.recommendations.map { it.track }
        externalPlayerLauncher.openPlaylist("AudOneOut For You", tracks)
            .onFailure { setMessage(it.message ?: "No music player could open this mix") }
    }

    fun saveRadioStation(name: String, url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            setMessage("Enter a full http:// or https:// stream address")
            return
        }
        viewModelScope.launch {
            repository.saveRadioStation(name.ifBlank { UriLabel.from(url) }, url)
            setMessage("Radio station saved")
        }
    }

    fun openRadioStation(station: RadioStationEntity) {
        viewModelScope.launch {
            radioStreamResolver.resolve(station.streamUrl)
                .onSuccess { stream ->
                    externalPlayerLauncher.openStream(station.name, stream)
                        .onFailure { setMessage(it.message ?: "No music player could open this stream") }
                }
                .onFailure { setMessage(it.message ?: "Could not open this radio stream") }
            repository.markRadioStationPlayed(station.id)
        }
    }

    fun openRadioUrl(name: String, url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        viewModelScope.launch {
            radioStreamResolver.resolve(url)
                .onSuccess { stream ->
                    externalPlayerLauncher.openStream(name, stream)
                        .onFailure { setMessage(it.message ?: "No music player could open this stream") }
                }
                .onFailure { setMessage(it.message ?: "Could not open this radio stream") }
        }
    }

    fun deleteRadioStation(stationId: Long) {
        viewModelScope.launch { repository.deleteRadioStation(stationId) }
    }

    fun setMixtapePrompt(prompt: String) {
        _uiState.update { it.copy(mixtapePrompt = prompt) }
    }

    fun generateMixtape() = launchBusy {
        val tracks = repository.availableTracksOnce()
        if (tracks.isEmpty()) {
            _uiState.update { it.copy(mixtapeRules = null, mixtapeCandidates = emptyList(), mixtapeMessage = "Scan music first.") }
            return@launchBusy
        }
        val favoriteIds = repository.favoriteTracksOnce().mapTo(mutableSetOf()) { it.id }
        val events = repository.listeningEventsOnce()
        val summary = LibrarySummary(
            genres = tracks.map { it.genre }.filter { it.isNotBlank() }.distinct(),
            languages = tracks.map { it.language }.filter { it.isNotBlank() }.distinct(),
            folders = tracks.map { it.folder }.filter { it.isNotBlank() }.distinct(),
            formats = tracks.map { it.format.ifBlank { it.mimeType.substringAfterLast('/') } }.filter { it.isNotBlank() }.distinct(),
            trackCount = tracks.size
        )
        promptInterpreter.interpret(_uiState.value.mixtapePrompt, summary)
            .onSuccess { rules ->
                val candidates = playlistGenerator.generate(
                    tracks = tracks,
                    rules = rules,
                    favoriteIds = favoriteIds,
                    events = events
                )
                _uiState.update {
                    it.copy(
                        mixtapeRules = rules,
                        mixtapeCandidates = candidates,
                        mixtapeMessage = "${candidates.size} tracks selected locally from ${tracks.size} indexed tracks."
                    )
                }
            }
            .onFailure { error ->
                _uiState.update { it.copy(mixtapeRules = null, mixtapeCandidates = emptyList(), mixtapeMessage = error.message ?: "Could not interpret prompt") }
            }
    }

    fun openMixtape() {
        val ids = _uiState.value.mixtapeCandidates.map { it.track.libraryId }
        openTracks("AudOneOut Mixtape", ids)
    }

    fun saveMixtape(name: String) {
        val state = _uiState.value
        if (state.mixtapeCandidates.isEmpty()) return
        viewModelScope.launch {
            repository.saveGeneratedPlaylist(
                name = name.ifBlank { "AudOneOut Mixtape" },
                rules = state.mixtapeRules ?: PlaylistRules(prompt = state.mixtapePrompt),
                candidates = state.mixtapeCandidates
            )
            setMessage("Mixtape saved to Playlists")
        }
    }

    fun setAutomaticLibraryChecking(enabled: Boolean) = viewModelScope.launch { settings.setAutomaticLibraryChecking(enabled) }
    fun setCheckFrequency(frequency: String) = viewModelScope.launch { settings.setCheckFrequency(frequency) }
    fun setWifiOnlyOnlineEnrichment(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnlyOnlineEnrichment(enabled) }
    fun setEnhanceNewTracksAutomatically(enabled: Boolean) = viewModelScope.launch { settings.setEnhanceNewTracksAutomatically(enabled) }
    fun setAnalyseOnlyWhileCharging(enabled: Boolean) = viewModelScope.launch { settings.setAnalyseOnlyWhileCharging(enabled) }
    fun setNotifyWhenNewTracksReady(enabled: Boolean) = viewModelScope.launch { settings.setNotifyWhenNewTracksReady(enabled) }
    fun setQuietBackgroundMode(enabled: Boolean) = viewModelScope.launch { settings.setQuietBackgroundMode(enabled) }
    fun setOnlineEnrichmentEnabled(enabled: Boolean) = viewModelScope.launch { settings.setOnlineEnrichmentEnabled(enabled) }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = "") }
    }

    private fun startLocalRadio(seeds: List<TrackEntity>, label: String) {
        viewModelScope.launch { generateAndOpenRadio(seeds, label) }
    }

    private fun publishLastFmDiscovery(result: LastFmDiscoveryResult) {
        lastFmTasteSeeds = result.localTasteSeeds
        _uiState.update {
            it.copy(
                lastFmProfile = result.profile,
                lastFmRecommendations = result.recommendations,
                localLastFmTasteSeedCount = result.localTasteSeeds.size,
                recentLastFmScrobbleCount = result.recentScrobbleCount,
                lastFmLoading = false,
                lastFmError = ""
            )
        }
        viewModelScope.launch {
            val tracks = repository.availableTracksOnce()
            val favorites = repository.favoriteTracksOnce()
            val events = repository.listeningEventsOnce()
            _uiState.update { it.copy(recommendations = buildLocalRecommendations(tracks, favorites, events)) }
        }
    }

    private fun buildLocalRecommendations(
        tracks: List<TrackEntity>,
        favorites: List<TrackEntity>,
        events: List<ListeningEventEntity>
    ): List<TrackRecommendation> {
        val availableById = tracks.associateBy { it.id }
        val onlineSeeds = lastFmTasteSeeds.mapNotNull { availableById[it.id] }.distinctBy { it.id }
        val seeds = (favorites + onlineSeeds)
            .distinctBy { it.id }
            .ifEmpty { tracks.sortedByDescending { it.dateAddedSeconds }.take(6) }
        val daySeed = System.currentTimeMillis() / 86_400_000L
        return recommender.recommend(
            library = tracks,
            seeds = seeds,
            favorites = favorites,
            events = events,
            limit = 30,
            onlineSeedIds = onlineSeeds.mapTo(mutableSetOf()) { it.id },
            explorationSeed = daySeed + recommendationRotation
        )
    }

    private suspend fun generateAndOpenRadio(seeds: List<TrackEntity>, label: String) {
        if (seeds.isEmpty()) return
        val tracks = repository.availableTracksOnce()
        val favorites = repository.favoriteTracksOnce()
        val events = repository.listeningEventsOnce()
        val recommendations = recommender.recommend(tracks, seeds, favorites, events, 50)
        val queue = (seeds.take(1) + recommendations.map { it.track }).distinctBy { it.id }
        _uiState.update { it.copy(localRadio = recommendations, radioSeedLabel = label) }
        externalPlayerLauncher.openPlaylist("Radio from $label", queue)
            .onFailure { setMessage(it.message ?: "No music player could open this radio mix") }
    }

    private fun publishLibraryContent() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                tracks = allTracks.filterAndSortTracks(state.libraryQuery, state.librarySort),
                albums = allAlbums.filterAndSortAlbums(state.libraryQuery, state.librarySort),
                artists = allArtists.filterAndSortArtists(state.libraryQuery, state.librarySort),
                folders = allFolders.filterAndSortFolders(state.libraryQuery, state.librarySort)
            )
        }
    }

    private fun launchBusy(successMessage: String = "", block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            runCatching { block() }
                .onSuccess { if (successMessage.isNotBlank()) setMessage(successMessage) }
                .onFailure { setMessage(it.message ?: "Something went wrong") }
            _uiState.update { it.copy(busy = false) }
        }
    }

    private fun setMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }
}

private object UriLabel {
    fun from(url: String): String = url.substringAfter("://").substringBefore('/').ifBlank { "Online radio" }
}

private fun List<TrackEntity>.filterAndSortTracks(query: String, sort: LibrarySort): List<TrackEntity> {
    val filtered = if (query.isBlank()) this else filter {
        it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) || it.relativePath.contains(query, true)
    }
    return when (sort) {
        LibrarySort.Title -> filtered.sortedBy { it.title.lowercase() }
        LibrarySort.Artist -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        LibrarySort.Album -> filtered.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() }))
        LibrarySort.RecentlyAdded -> filtered.sortedByDescending { it.dateAddedSeconds }
        LibrarySort.Duration -> filtered.sortedByDescending { it.durationMs }
        LibrarySort.Size -> filtered.sortedByDescending { it.sizeBytes }
    }
}

private fun List<AlbumEntity>.filterAndSortAlbums(query: String, sort: LibrarySort): List<AlbumEntity> {
    val filtered = if (query.isBlank()) this else filter { it.title.contains(query, true) || it.artistName.contains(query, true) }
    return when (sort) {
        LibrarySort.Artist -> filtered.sortedWith(compareBy({ it.artistName.lowercase() }, { it.title.lowercase() }))
        LibrarySort.RecentlyAdded -> filtered.sortedByDescending { it.lastAddedSeconds }
        LibrarySort.Duration -> filtered.sortedByDescending { it.durationMs }
        LibrarySort.Size -> filtered.sortedByDescending { it.storageBytes }
        else -> filtered.sortedBy { it.title.lowercase() }
    }
}

private fun List<ArtistEntity>.filterAndSortArtists(query: String, sort: LibrarySort): List<ArtistEntity> {
    val filtered = if (query.isBlank()) this else filter { it.name.contains(query, true) }
    return when (sort) {
        LibrarySort.Duration -> filtered.sortedByDescending { it.durationMs }
        LibrarySort.Size -> filtered.sortedByDescending { it.trackCount }
        LibrarySort.Album -> filtered.sortedByDescending { it.albumCount }
        else -> filtered.sortedBy { it.name.lowercase() }
    }
}

private fun List<FolderEntity>.filterAndSortFolders(query: String, sort: LibrarySort): List<FolderEntity> {
    val filtered = if (query.isBlank()) this else filter { it.name.contains(query, true) || it.path.contains(query, true) }
    return when (sort) {
        LibrarySort.Size -> filtered.sortedByDescending { it.storageBytes }
        LibrarySort.Duration -> filtered.sortedByDescending { it.trackCount }
        else -> filtered.sortedBy { it.name.lowercase() }
    }
}
