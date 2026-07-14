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
    val albums: Flow<List<AlbumEntity>> = dao.observeAlbums()
    val artists: Flow<List<ArtistEntity>> = dao.observeArtists()
    val folders: Flow<List<FolderEntity>> = dao.observeFolders()
    val inbox: Flow<List<TrackEntity>> = dao.observeInbox()
    val roots: Flow<List<MusicRootEntity>> = dao.observeMusicRoots()
    val blacklistRules: Flow<List<FolderBlacklistRuleEntity>> = dao.observeBlacklistRules()

    suspend fun ensureDefaultBlacklist() {
        if (dao.getAllBlacklistRulesOnce().isEmpty()) {
            dao.upsertBlacklistRules(BlacklistMatcher.defaultRules)
        }
        refreshBlacklistPreviewCounts()
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

    suspend fun setMusicRootIncluded(rootId: Long, included: Boolean) {
        dao.updateMusicRootIncluded(rootId, included)
    }

    suspend fun removeMusicRoot(rootId: Long) {
        dao.deleteMusicRoot(rootId)
    }

    suspend fun addFolderNameRule(name: String) {
        dao.upsertBlacklistRule(
            FolderBlacklistRuleEntity(
                label = name,
                pattern = name,
                matchType = BlacklistMatcher.FOLDER_NAME
            )
        )
        refreshBlacklistPreviewCounts()
    }

    suspend fun setBlacklistRuleEnabled(ruleId: Long, enabled: Boolean) {
        dao.updateBlacklistRuleEnabled(ruleId, enabled)
    }

    suspend fun deleteBlacklistRule(ruleId: Long) {
        dao.deleteBlacklistRule(ruleId)
    }

    suspend fun restoreDefaultBlacklist() {
        val existingKeys = dao.getAllBlacklistRulesOnce()
            .map { "${it.matchType}|${it.pattern.lowercase()}" }
            .toSet()
        val missingDefaults = BlacklistMatcher.defaultRules.filter { rule ->
            "${rule.matchType}|${rule.pattern.lowercase()}" !in existingKeys
        }
        if (missingDefaults.isNotEmpty()) {
            dao.upsertBlacklistRules(missingDefaults)
        }
        refreshBlacklistPreviewCounts()
    }

    suspend fun scanMediaStore() {
        ensureDefaultBlacklist()
        val rules = dao.getEnabledBlacklistRules()
        val includedRoots = dao.observeMusicRootsOnce().filter { it.included }
        val jobId = dao.insertScanJob(
            ScanJobEntity(
                type = "FullMediaStore",
                status = "Running",
                startedAtMillis = System.currentTimeMillis()
            )
        )
        var finalProgress = ScanProgress()
        if (includedRoots.isEmpty()) {
            scanner.scanAudio(rules).collect { result ->
                finalProgress = result.progress
                _scanProgress.value = result.progress
                dao.upsertTracks(result.tracks)
            }
        } else {
            var aggregateProgress = ScanProgress(estimatedRemainingWork = "Scanning included roots")
            includedRoots.forEach { root ->
                val rootProgress = scanRootInternal(root, rules)
                aggregateProgress = aggregateProgress.copy(
                    currentRoot = root.displayName,
                    currentFolder = rootProgress.currentFolder,
                    tracksFound = aggregateProgress.tracksFound + rootProgress.tracksFound,
                    newTracks = aggregateProgress.newTracks + rootProgress.newTracks,
                    updatedTracks = aggregateProgress.updatedTracks + rootProgress.updatedTracks,
                    excludedTracks = aggregateProgress.excludedTracks + rootProgress.excludedTracks,
                    failedTracks = aggregateProgress.failedTracks + rootProgress.failedTracks,
                    estimatedRemainingWork = "Scanning included roots"
                )
                finalProgress = aggregateProgress
            }
        }
        refreshLibraryFacets()
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

    suspend fun scanMusicRoot(rootId: Long) {
        ensureDefaultBlacklist()
        val root = dao.findMusicRoot(rootId) ?: return
        val rules = dao.getEnabledBlacklistRules()
        scanRootInternal(root, rules)
        refreshLibraryFacets()
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

    suspend fun refreshLibraryFacets() {
        val facets = LibraryFacetBuilder.build(dao.getAllTracksOnce())
        dao.replaceLibraryFacets(facets.albums, facets.artists, facets.folders)
        refreshBlacklistPreviewCounts()
    }

    private suspend fun scanRootInternal(
        root: MusicRootEntity,
        rules: List<FolderBlacklistRuleEntity>
    ): ScanProgress {
        var finalProgress = ScanProgress(currentRoot = root.displayName, estimatedRemainingWork = "Queued")
        scanner.scanAudio(rules, rootFilter = root.location).collect { result ->
            finalProgress = result.progress.copy(currentRoot = root.displayName)
            _scanProgress.value = finalProgress
            dao.upsertTracks(result.tracks.map { it.copy(rootId = root.id) })
        }
        val tracksForRoot = dao.getAllTracksOnce().filter { it.rootId == root.id && it.availability == "Available" }
        dao.updateMusicRootScanSummary(
            rootId = root.id,
            scanStatus = "Complete",
            lastScanTimeMillis = System.currentTimeMillis(),
            indexedTrackCount = tracksForRoot.size,
            storageBytes = tracksForRoot.sumOf { it.sizeBytes }
        )
        return finalProgress
    }

    private suspend fun refreshBlacklistPreviewCounts() {
        val tracks = dao.getAllTracksOnce()
        dao.getAllBlacklistRulesOnce().forEach { rule ->
            val previewRule = rule.copy(enabled = true)
            val excludedCount = tracks.count { track ->
                BlacklistMatcher.isExcluded(track.relativePath, listOf(previewRule))
            }
            dao.updateBlacklistPreviewCount(rule.id, excludedCount)
        }
    }
}
