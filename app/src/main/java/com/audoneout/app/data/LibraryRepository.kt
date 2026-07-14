package com.audoneout.app.data

import com.audoneout.app.domain.LibraryHealth
import com.audoneout.app.domain.ScanProgress
import com.audoneout.app.scan.BlacklistMatcher
import com.audoneout.app.scan.MediaStoreScanner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class LibraryCounts(
    val tracks: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val folders: Int = 0
)

@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
    private val scanner: MediaStoreScanner
) {
    private val _scanProgress = MutableStateFlow(ScanProgress(estimatedRemainingWork = "Ready"))
    val scanProgress: Flow<ScanProgress> = _scanProgress

    val counts: Flow<LibraryCounts> = combine(
        dao.observeTrackCount(),
        dao.observeAlbumCount(),
        dao.observeArtistCount(),
        dao.observeFolderCount()
    ) { tracks, albums, artists, folders ->
        LibraryCounts(tracks, albums, artists, folders)
    }

    val tracks: Flow<List<TrackEntity>> = dao.observeTracks()
    val inbox: Flow<List<TrackEntity>> = dao.observeInbox()
    val roots: Flow<List<MusicRootEntity>> = dao.observeMusicRoots()
    val blacklistRules: Flow<List<FolderBlacklistRuleEntity>> = dao.observeBlacklistRules()

    suspend fun ensureDefaultBlacklist() {
        if (dao.getEnabledBlacklistRules().isEmpty()) {
            dao.upsertBlacklistRules(BlacklistMatcher.defaultRules)
        }
    }

    suspend fun addMusicRoot(displayName: String, uri: String, location: String = uri) {
        dao.upsertMusicRoot(
            MusicRootEntity(
                displayName = displayName,
                uri = uri,
                location = location,
                scanStatus = "Ready"
            )
        )
    }

    suspend fun addFolderNameRule(name: String) {
        dao.upsertBlacklistRule(
            FolderBlacklistRuleEntity(
                label = name,
                pattern = name,
                matchType = BlacklistMatcher.FOLDER_NAME
            )
        )
    }

    suspend fun scanMediaStore() {
        ensureDefaultBlacklist()
        val rules = dao.getEnabledBlacklistRules()
        val jobId = dao.insertScanJob(
            ScanJobEntity(
                type = "FullMediaStore",
                status = "Running",
                startedAtMillis = System.currentTimeMillis()
            )
        )
        var finalProgress = ScanProgress()
        scanner.scanAudio(rules).collect { result ->
            finalProgress = result.progress
            _scanProgress.value = result.progress
            dao.upsertTracks(result.tracks)
        }
        dao.finishScanJob(
            scanJobId = jobId,
            status = "Complete",
            finishedAtMillis = System.currentTimeMillis(),
            tracksFound = finalProgress.tracksFound,
            newTracks = finalProgress.newTracks,
            updatedTracks = finalProgress.updatedTracks,
            excludedTracks = finalProgress.excludedTracks,
            failedTracks = finalProgress.failedTracks
        )
        _scanProgress.value = finalProgress.copy(estimatedRemainingWork = "Last scan complete")
    }

    suspend fun currentHealth(): LibraryHealth {
        val allTracks = dao.getAllTracksOnce()
        val missing = allTracks.count {
            it.title.isBlank() || it.artist.isBlank() || it.album.isBlank() ||
                it.artist.equals("<unknown>", ignoreCase = true)
        }
        val duplicateCandidates = allTracks
            .groupBy { "${it.title.lowercase()}|${it.artist.lowercase()}|${it.album.lowercase()}|${it.durationMs / 1000}" }
            .count { (_, matches) -> matches.size > 1 }
        val issueCount = missing + duplicateCandidates
        val score = (100 - issueCount.coerceAtMost(100)).coerceIn(0, 100)
        return LibraryHealth(score, issueCount, duplicateCandidates, missing)
    }
}

