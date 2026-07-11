package com.audoneout.app

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LibrarySnapshot(
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val folderCount: Int = 0
)

data class MainUiState(
    val library: LibrarySnapshot = LibrarySnapshot(),
    val nowPlayingTitle: String = "Nothing playing",
    val scanMessage: String = "Local audio scanning arrives in Milestone 2."
)

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
}
