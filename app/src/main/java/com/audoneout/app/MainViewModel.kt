package com.audoneout.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audoneout.app.data.AlbumEntity
import com.audoneout.app.data.ArtistEntity
import com.audoneout.app.data.FolderBlacklistRuleEntity
import com.audoneout.app.data.FolderEntity
import com.audoneout.app.data.LibraryRepository
import com.audoneout.app.data.MusicRootEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.domain.LibraryHealth
import com.audoneout.app.domain.ScanProgress
import com.audoneout.app.settings.AppSettings
import com.audoneout.app.settings.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val roots: List<MusicRootEntity> = emptyList(),
    val blacklistRules: List<FolderBlacklistRuleEntity> = emptyList(),
    val libraryView: LibraryView = LibraryView.Songs,
    val librarySort: LibrarySort = LibrarySort.Title,
    val libraryQuery: String = "",
    val libraryGridMode: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(estimatedRemainingWork = "Ready"),
    val settings: SettingsState = SettingsState(),
    val health: LibraryHealth = LibraryHealth(score = 100, issueCount = 0, duplicateCandidates = 0, missingMetadata = 0),
    val nowPlayingTitle: String = "Nothing playing",
    val scanMessage: String = "Choose music access, then scan your local library.",
    val busy: Boolean = false
)

enum class LibraryView {
    Songs,
    Albums,
    Artists,
    Folders
}

enum class LibrarySort {
    Title,
    Artist,
    Album,
    RecentlyAdded,
    Duration,
    Size
}

private data class LibraryUiBundle(
    val library: LibrarySnapshot,
    val tracks: List<TrackEntity>,
    val albums: List<AlbumEntity>,
    val artists: List<ArtistEntity>,
    val folders: List<FolderEntity>,
    val inbox: List<TrackEntity>,
    val roots: List<MusicRootEntity>
)

private data class LibraryControls(
    val view: LibraryView = LibraryView.Songs,
    val sort: LibrarySort = LibrarySort.Title,
    val query: String = "",
    val gridMode: Boolean = false
)

private data class LibraryContentBundle(
    val library: LibrarySnapshot,
    val tracks: List<TrackEntity>,
    val albums: List<AlbumEntity>,
    val artists: List<ArtistEntity>,
    val folders: List<FolderEntity>
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val settings: AppSettings
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val _health = MutableStateFlow(LibraryHealth(score = 100, issueCount = 0, duplicateCandidates = 0, missingMetadata = 0))
    private val _busy = MutableStateFlow(false)
    private val _libraryView = MutableStateFlow(LibraryView.Songs)
    private val _librarySort = MutableStateFlow(LibrarySort.Title)
    private val _libraryQuery = MutableStateFlow("")
    private val _libraryGridMode = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repository.ensureDefaultBlacklist()
        }
        viewModelScope.launch {
            val libraryContent = combine(
                repository.counts,
                repository.tracks,
                repository.albums,
                repository.artists,
                repository.folders
            ) { counts, tracks, albums, artists, folders ->
                LibraryContentBundle(
                    library = LibrarySnapshot(counts.tracks, counts.albums, counts.artists, counts.folders),
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    folders = folders
                )
            }

            val libraryBundle = combine(
                libraryContent,
                repository.inbox,
                repository.roots
            ) { content, inbox, roots ->
                LibraryUiBundle(
                    library = content.library,
                    tracks = content.tracks,
                    albums = content.albums,
                    artists = content.artists,
                    folders = content.folders,
                    inbox = inbox,
                    roots = roots
                )
            }

            val controls = combine(
                _libraryView,
                _librarySort,
                _libraryQuery,
                _libraryGridMode
            ) { view, sort, query, gridMode ->
                LibraryControls(view, sort, query, gridMode)
            }

            val stateWithoutHealth = combine(
                libraryBundle,
                repository.blacklistRules,
                repository.scanProgress,
                settings.state,
                controls
            ) { bundle, rules, progress, settingsState, libraryControls ->
                MainUiState(
                    library = bundle.library,
                    tracks = bundle.tracks.filterAndSortTracks(libraryControls.query, libraryControls.sort),
                    albums = bundle.albums.filterAndSortAlbums(libraryControls.query, libraryControls.sort),
                    artists = bundle.artists.filterAndSortArtists(libraryControls.query, libraryControls.sort),
                    folders = bundle.folders.filterAndSortFolders(libraryControls.query, libraryControls.sort),
                    inbox = bundle.inbox,
                    roots = bundle.roots,
                    blacklistRules = rules,
                    libraryView = libraryControls.view,
                    librarySort = libraryControls.sort,
                    libraryQuery = libraryControls.query,
                    libraryGridMode = libraryControls.gridMode,
                    scanProgress = progress,
                    settings = settingsState,
                    scanMessage = progress.estimatedRemainingWork
                )
            }

            combine(stateWithoutHealth, _health, _busy) { state, health, busy ->
                state.copy(health = health, busy = busy)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repository.scanMediaStore() }
            val health = repository.currentHealth()
            _health.value = health
            _busy.value = false
        }
    }

    fun addRoot(displayName: String, uri: String, location: String = uri) {
        viewModelScope.launch {
            repository.addMusicRoot(displayName, uri, location)
        }
    }

    fun setRootIncluded(rootId: Long, included: Boolean) {
        viewModelScope.launch {
            repository.setMusicRootIncluded(rootId, included)
        }
    }

    fun rescanRoot(rootId: Long) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repository.scanMusicRoot(rootId) }
            _health.value = repository.currentHealth()
            _busy.value = false
        }
    }

    fun removeRoot(rootId: Long) {
        viewModelScope.launch {
            repository.removeMusicRoot(rootId)
        }
    }

    fun addBlacklistRule(name: String) {
        viewModelScope.launch {
            repository.addFolderNameRule(name)
        }
    }

    fun setBlacklistRuleEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setBlacklistRuleEnabled(ruleId, enabled)
        }
    }

    fun deleteBlacklistRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteBlacklistRule(ruleId)
        }
    }

    fun restoreDefaultBlacklist() {
        viewModelScope.launch {
            repository.restoreDefaultBlacklist()
        }
    }

    fun setLibraryView(view: LibraryView) {
        _libraryView.value = view
    }

    fun setLibrarySort(sort: LibrarySort) {
        _librarySort.value = sort
    }

    fun setLibraryQuery(query: String) {
        _libraryQuery.value = query
    }

    fun toggleLibraryGridMode() {
        _libraryGridMode.value = !_libraryGridMode.value
    }
}

private fun List<TrackEntity>.filterAndSortTracks(query: String, sort: LibrarySort): List<TrackEntity> {
    val filtered = if (query.isBlank()) {
        this
    } else {
        filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true) ||
                it.relativePath.contains(query, ignoreCase = true)
        }
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
    val filtered = if (query.isBlank()) this else filter {
        it.title.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true)
    }
    return when (sort) {
        LibrarySort.Artist -> filtered.sortedWith(compareBy({ it.artistName.lowercase() }, { it.title.lowercase() }))
        LibrarySort.RecentlyAdded -> filtered.sortedByDescending { it.lastAddedSeconds }
        LibrarySort.Duration -> filtered.sortedByDescending { it.durationMs }
        LibrarySort.Size -> filtered.sortedByDescending { it.storageBytes }
        else -> filtered.sortedBy { it.title.lowercase() }
    }
}

private fun List<ArtistEntity>.filterAndSortArtists(query: String, sort: LibrarySort): List<ArtistEntity> {
    val filtered = if (query.isBlank()) this else filter { it.name.contains(query, ignoreCase = true) }
    return when (sort) {
        LibrarySort.Duration -> filtered.sortedByDescending { it.durationMs }
        LibrarySort.Size -> filtered.sortedByDescending { it.trackCount }
        LibrarySort.Album -> filtered.sortedByDescending { it.albumCount }
        else -> filtered.sortedBy { it.name.lowercase() }
    }
}

private fun List<FolderEntity>.filterAndSortFolders(query: String, sort: LibrarySort): List<FolderEntity> {
    val filtered = if (query.isBlank()) this else filter {
        it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true)
    }
    return when (sort) {
        LibrarySort.Size -> filtered.sortedByDescending { it.storageBytes }
        LibrarySort.Duration -> filtered.sortedByDescending { it.trackCount }
        else -> filtered.sortedBy { it.name.lowercase() }
    }
}
