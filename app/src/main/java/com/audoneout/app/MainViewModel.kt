package com.audoneout.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audoneout.app.data.FolderBlacklistRuleEntity
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
    val inbox: List<TrackEntity> = emptyList(),
    val roots: List<MusicRootEntity> = emptyList(),
    val blacklistRules: List<FolderBlacklistRuleEntity> = emptyList(),
    val scanProgress: ScanProgress = ScanProgress(estimatedRemainingWork = "Ready"),
    val settings: SettingsState = SettingsState(),
    val health: LibraryHealth = LibraryHealth(score = 100, issueCount = 0, duplicateCandidates = 0, missingMetadata = 0),
    val nowPlayingTitle: String = "Nothing playing",
    val scanMessage: String = "Choose music access, then scan your local library.",
    val busy: Boolean = false
)

private data class LibraryUiBundle(
    val library: LibrarySnapshot,
    val tracks: List<TrackEntity>,
    val inbox: List<TrackEntity>,
    val roots: List<MusicRootEntity>
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val settings: AppSettings
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultBlacklist()
        }
        viewModelScope.launch {
            val libraryBundle = combine(
                repository.counts,
                repository.tracks,
                repository.inbox,
                repository.roots
            ) { counts, tracks, inbox, roots ->
                LibraryUiBundle(
                    library = LibrarySnapshot(counts.tracks, counts.albums, counts.artists, counts.folders),
                    tracks = tracks,
                    inbox = inbox,
                    roots = roots
                )
            }

            combine(
                libraryBundle,
                repository.blacklistRules,
                repository.scanProgress,
                settings.state
            ) { bundle, rules, progress, settingsState ->
                MainUiState(
                    library = bundle.library,
                    tracks = bundle.tracks,
                    inbox = bundle.inbox,
                    roots = bundle.roots,
                    blacklistRules = rules,
                    scanProgress = progress,
                    settings = settingsState,
                    scanMessage = progress.estimatedRemainingWork
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            runCatching { repository.scanMediaStore() }
            val health = repository.currentHealth()
            _uiState.value = _uiState.value.copy(busy = false, health = health)
        }
    }

    fun addRoot(displayName: String, uri: String) {
        viewModelScope.launch {
            repository.addMusicRoot(displayName, uri)
        }
    }

    fun addBlacklistRule(name: String) {
        viewModelScope.launch {
            repository.addFolderNameRule(name)
        }
    }
}
