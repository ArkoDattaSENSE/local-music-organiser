package com.audoneout.app.data

import com.audoneout.app.domain.LibraryHealth
import com.audoneout.app.domain.ScanProgress
import com.audoneout.app.doctor.LibraryDoctor
import com.audoneout.app.scan.BlacklistMatcher
import com.audoneout.app.scan.MediaStoreScanner
import com.audoneout.app.domain.PlaylistCandidate
import com.audoneout.app.domain.PlaylistRules
import com.audoneout.app.metadata.LocalMetadataEnricher
import com.audoneout.app.metadata.TrackMetadataInput
import com.audoneout.app.settings.AppSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LibraryCounts(
    val tracks: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val folders: Int = 0
)

@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
    private val scanner: MediaStoreScanner,
    private val doctor: LibraryDoctor,
    private val settings: AppSettings
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
    val favoriteTracks: Flow<List<TrackEntity>> = dao.observeFavoriteTracks()
    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()
    val radioStations: Flow<List<RadioStationEntity>> = dao.observeRadioStations()
    val latestCompletedScan: Flow<ScanJobEntity?> = dao.observeLatestCompletedScan()
    val albums: Flow<List<AlbumEntity>> = dao.observeAlbums()
    val artists: Flow<List<ArtistEntity>> = dao.observeArtists()
    val folders: Flow<List<FolderEntity>> = dao.observeFolders()
    val inbox: Flow<List<TrackEntity>> = dao.observeInbox()
    val analysisResults: Flow<List<AnalysisResultEntity>> = dao.observeAnalysisResults()
    val metadataSuggestions: Flow<List<MetadataSuggestionEntity>> = dao.observeMetadataSuggestions()
    val userConfirmedMetadata: Flow<List<UserConfirmedMetadataEntity>> = dao.observeUserConfirmedMetadata()
    val metadataWritebacks: Flow<List<MetadataWritebackEntity>> = dao.observeMetadataWritebacks()
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
        reconcileExclusions()
        refreshLibraryFacets()
        runLibraryDoctor()
    }

    suspend fun removeMusicRoot(rootId: Long) {
        val now = System.currentTimeMillis()
        dao.getAllTracksOnce().filter { it.rootId == rootId }.forEach { track ->
            dao.upsertTrackAvailability(
                TrackAvailabilityEntity(
                    trackId = track.id,
                    state = "Missing",
                    firstMissingAtMillis = now,
                    lastCheckedAtMillis = now
                )
            )
        }
        dao.markTracksForRemovedRootMissing(rootId)
        dao.deleteMusicRoot(rootId)
        refreshLibraryFacets()
        runLibraryDoctor()
    }

    suspend fun addFolderNameRule(name: String) {
        dao.upsertBlacklistRule(
            FolderBlacklistRuleEntity(
                label = name,
                pattern = name,
                matchType = BlacklistMatcher.FOLDER_NAME
            )
        )
        reconcileExclusions()
        refreshLibraryFacets()
        runLibraryDoctor()
    }

    suspend fun addFolderPathRule(label: String, path: String) {
        val cleanPath = path.trim('/')
        if (cleanPath.isBlank()) return
        dao.upsertBlacklistRule(
            FolderBlacklistRuleEntity(
                label = label.ifBlank { cleanPath.substringAfterLast('/') },
                pattern = cleanPath,
                matchType = BlacklistMatcher.DESCENDANTS
            )
        )
        reconcileExclusions()
        refreshLibraryFacets()
        runLibraryDoctor()
    }

    suspend fun setBlacklistRuleEnabled(ruleId: Long, enabled: Boolean) {
        dao.updateBlacklistRuleEnabled(ruleId, enabled)
        reconcileExclusions()
        refreshLibraryFacets()
        runLibraryDoctor()
    }

    suspend fun deleteBlacklistRule(ruleId: Long) {
        dao.deleteBlacklistRule(ruleId)
        reconcileExclusions()
        refreshLibraryFacets()
        runLibraryDoctor()
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
        reconcileExclusions()
        refreshLibraryFacets()
        runLibraryDoctor()
    }

    suspend fun scanMediaStore(): ScanProgress {
        ensureDefaultBlacklist()
        val rules = dao.getEnabledBlacklistRules()
        val includedRoots = dao.observeMusicRootsOnce().filter { it.included }
        val localEnrichmentEnabled = settings.state.first().enhanceNewTracksAutomatically
        val localEnrichedTrackIds = dao.getAllEnrichedMetadataOnce()
            .filter { it.source == LocalMetadataEnricher.SOURCE }
            .mapTo(mutableSetOf()) { it.trackId }
        val jobId = dao.insertScanJob(
            ScanJobEntity(
                type = "FullMediaStore",
                status = "Running",
                startedAtMillis = System.currentTimeMillis()
            )
        )
        var finalProgress = ScanProgress()
        try {
            if (includedRoots.isEmpty()) {
                val existing = dao.getAllTracksOnce().associateBy { it.mediaKey }.toMutableMap()
                val seen = mutableSetOf<String>()
                var newTracks = 0
                var updatedTracks = 0
                scanner.scanAudio(rules).collect { result ->
                    val persisted = persistScannedBatch(
                        result.tracks,
                        null,
                        existing,
                        jobId,
                        localEnrichedTrackIds,
                        localEnrichmentEnabled
                    )
                    seen += persisted.seenContentUris
                    newTracks += persisted.newTracks
                    updatedTracks += persisted.updatedTracks
                    finalProgress = result.progress.copy(newTracks = newTracks, updatedTracks = updatedTracks)
                    _scanProgress.value = finalProgress
                }
                val missing = recordMissingTracks(
                    tracks = existing.values.filter { it.rootId == null && it.contentUri !in seen },
                    scanJobId = jobId
                )
                if (seen.isEmpty()) {
                    dao.markAllGlobalTracksMissing()
                } else {
                    dao.markUnseenGlobalTracksMissing(seen.toList())
                }
                finalProgress = finalProgress.copy(missingTracks = missing)
            } else {
                var aggregateProgress = ScanProgress(estimatedRemainingWork = "Scanning included roots")
                includedRoots.forEach { root ->
                    val rootProgress = scanRootInternal(
                        root,
                        rules,
                        jobId,
                        localEnrichedTrackIds,
                        localEnrichmentEnabled
                    )
                    aggregateProgress = aggregateProgress.copy(
                        currentRoot = root.displayName,
                        currentFolder = rootProgress.currentFolder,
                        tracksFound = aggregateProgress.tracksFound + rootProgress.tracksFound,
                        newTracks = aggregateProgress.newTracks + rootProgress.newTracks,
                        updatedTracks = aggregateProgress.updatedTracks + rootProgress.updatedTracks,
                        missingTracks = aggregateProgress.missingTracks + rootProgress.missingTracks,
                        excludedTracks = aggregateProgress.excludedTracks + rootProgress.excludedTracks,
                        failedTracks = aggregateProgress.failedTracks + rootProgress.failedTracks,
                        estimatedRemainingWork = "Scanning included roots"
                    )
                    finalProgress = aggregateProgress
                }
            }
            refreshLibraryFacets()
            runLibraryDoctor()
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
            return finalProgress
        } catch (error: Throwable) {
            val status = if (error is CancellationException) "Paused" else "Failed"
            withContext(NonCancellable) {
                dao.finishScanJob(
                    scanJobId = jobId,
                    status = status,
                    finishedAtMillis = System.currentTimeMillis(),
                    tracksFound = finalProgress.tracksFound,
                    newTracks = finalProgress.newTracks,
                    updatedTracks = finalProgress.updatedTracks,
                    excludedTracks = finalProgress.excludedTracks,
                    failedTracks = finalProgress.failedTracks + if (status == "Failed") 1 else 0
                )
                _scanProgress.value = finalProgress.copy(
                    failedTracks = finalProgress.failedTracks + if (status == "Failed") 1 else 0,
                    estimatedRemainingWork = if (status == "Paused") "Scan paused" else "Scan failed"
                )
            }
            throw error
        }
    }

    fun markScanPaused() {
        _scanProgress.value = _scanProgress.value.copy(estimatedRemainingWork = "Scan paused; run again to resume")
    }

    suspend fun scanMusicRoot(rootId: Long) {
        ensureDefaultBlacklist()
        val root = dao.findMusicRoot(rootId) ?: return
        val rules = dao.getEnabledBlacklistRules()
        val jobId = dao.insertScanJob(
            ScanJobEntity(
                rootId = root.id,
                type = "MusicRoot",
                status = "Running",
                startedAtMillis = System.currentTimeMillis()
            )
        )
        val localEnrichedTrackIds = dao.getAllEnrichedMetadataOnce()
            .filter { it.source == LocalMetadataEnricher.SOURCE }
            .mapTo(mutableSetOf()) { it.trackId }
        var progress = ScanProgress(currentRoot = root.displayName)
        try {
            progress = scanRootInternal(
                root,
                rules,
                jobId,
                localEnrichedTrackIds,
                settings.state.first().enhanceNewTracksAutomatically
            )
            refreshLibraryFacets()
            runLibraryDoctor()
            dao.finishScanJob(
                scanJobId = jobId,
                status = "Complete",
                finishedAtMillis = System.currentTimeMillis(),
                tracksFound = progress.tracksFound,
                newTracks = progress.newTracks,
                updatedTracks = progress.updatedTracks,
                excludedTracks = progress.excludedTracks,
                failedTracks = progress.failedTracks
            )
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                dao.finishScanJob(
                    scanJobId = jobId,
                    status = if (error is CancellationException) "Paused" else "Failed",
                    finishedAtMillis = System.currentTimeMillis(),
                    tracksFound = progress.tracksFound,
                    newTracks = progress.newTracks,
                    updatedTracks = progress.updatedTracks,
                    excludedTracks = progress.excludedTracks,
                    failedTracks = progress.failedTracks + if (error is CancellationException) 0 else 1
                )
            }
            throw error
        }
    }

    suspend fun currentHealth(): LibraryHealth {
        val allTracks = dao.getAllTracksOnce()
        val issues = doctor.analyse(allTracks.filter { it.availability == "Available" })
        val missing = issues.count { it.type.contains("Unknown", ignoreCase = true) || it.type.contains("Missing", ignoreCase = true) }
        val duplicateCandidates = issues.count { it.type == "Possible duplicate" }
        val issueCount = missing + duplicateCandidates
        val score = (100 - issueCount.coerceAtMost(100)).coerceIn(0, 100)
        return LibraryHealth(score, issueCount, duplicateCandidates, missing)
    }

    suspend fun availableTracksOnce(): List<TrackEntity> =
        dao.getAllTracksOnce().filter { it.availability == "Available" && it.enhancementStatus != "Excluded" }

    suspend fun favoriteTracksOnce(): List<TrackEntity> = dao.getFavoriteTracksOnce()

    suspend fun enrichedMetadataOnce(trackId: Long): List<EnrichedMetadataEntity> =
        dao.getEnrichedMetadataForTrack(trackId)

    suspend fun originalMetadataOnce(trackId: Long): OriginalMetadataEntity? =
        dao.findOriginalMetadata(trackId)

    suspend fun listeningEventsOnce(): List<ListeningEventEntity> = dao.getListeningEventsOnce()

    suspend fun playlistTracksOnce(playlistId: Long): List<TrackEntity> = dao.getPlaylistTracksOnce(playlistId)

    suspend fun unavailablePlaylistTrackCount(playlistId: Long): Int =
        dao.countUnavailablePlaylistTracks(playlistId)

    suspend fun toggleFavorite(trackId: Long) {
        if (dao.isFavorite(trackId)) {
            dao.deleteFavorite(trackId)
        } else {
            dao.upsertFavorite(FavoriteTrackEntity(trackId, 0, System.currentTimeMillis()))
        }
    }

    suspend fun savePlaylist(name: String, description: String, trackIds: List<Long>, reasons: Map<Long, String> = emptyMap()): Long {
        val now = System.currentTimeMillis()
        val playlistId = dao.insertPlaylist(
            PlaylistEntity(name = name, description = description, createdAtMillis = now, updatedAtMillis = now)
        )
        dao.replacePlaylistTracks(
            playlistId,
            trackIds.distinct().mapIndexed { index, trackId ->
                PlaylistTrackCrossRef(playlistId, trackId, index, reasons[trackId].orEmpty())
            }
        )
        return playlistId
    }

    suspend fun saveGeneratedPlaylist(
        name: String,
        rules: PlaylistRules,
        candidates: List<PlaylistCandidate>
    ): Long {
        val playlistId = savePlaylist(
            name = name,
            description = rules.prompt,
            trackIds = candidates.map { it.track.libraryId },
            reasons = candidates.associate { it.track.libraryId to it.reasons.joinToString() }
        )
        dao.insertPlaylistRule(
            PlaylistRuleEntity(
                playlistId = playlistId,
                prompt = rules.prompt,
                interpretedRulesJson = rules.toStorageJson(),
                createdAtMillis = System.currentTimeMillis()
            )
        )
        return playlistId
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val refs = dao.getPlaylistRefsOnce(playlistId)
        if (refs.any { it.trackId == trackId }) return
        dao.insertPlaylistTracks(listOf(PlaylistTrackCrossRef(playlistId, trackId, refs.size)))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        val refs = dao.getPlaylistRefsOnce(playlistId).filterNot { it.trackId == trackId }
        dao.replacePlaylistTracks(playlistId, refs.mapIndexed { index, ref -> ref.copy(position = index) })
    }

    suspend fun movePlaylistTrack(playlistId: Long, trackId: Long, direction: Int) {
        val refs = dao.getPlaylistRefsOnce(playlistId).toMutableList()
        val current = refs.indexOfFirst { it.trackId == trackId }
        if (current < 0) return
        val target = (current + direction).coerceIn(0, refs.lastIndex)
        if (target == current) return
        val item = refs.removeAt(current)
        refs.add(target, item)
        dao.replacePlaylistTracks(playlistId, refs.mapIndexed { index, ref -> ref.copy(position = index) })
    }

    suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

    suspend fun saveRadioStation(name: String, streamUrl: String): Long =
        dao.upsertRadioStation(RadioStationEntity(name = name, streamUrl = streamUrl))

    suspend fun deleteRadioStation(stationId: Long) = dao.deleteRadioStation(stationId)

    suspend fun markRadioStationPlayed(stationId: Long) =
        dao.markRadioStationPlayed(stationId, System.currentTimeMillis())

    suspend fun ignoreAnalysisResult(resultId: Long) = dao.ignoreAnalysisResult(resultId)

    suspend fun saveMetadataLookup(
        trackId: Long,
        source: String,
        values: Map<String, String>,
        externalId: String,
        externalUrl: String
    ): Int {
        val track = dao.findTrack(trackId) ?: return 0
        val now = System.currentTimeMillis()
        val cleanValues = values.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
        dao.deletePendingMetadataSuggestions(trackId, source)
        dao.insertEnrichedMetadata(
            cleanValues.map { (field, value) ->
                EnrichedMetadataEntity(
                    trackId = trackId,
                    key = field,
                    value = value,
                    source = source,
                    confidence = metadataConfidence(field),
                    createdAtMillis = now
                )
            }
        )
        val currentValues = mapOf(
            "title" to track.title,
            "artist" to track.artist,
            "album" to track.album,
            "genre" to track.genre
        )
        val suggestions = cleanValues
            .filterKeys { it in currentValues }
            .filter { (field, value) -> normalizeMetadata(value) != normalizeMetadata(currentValues[field].orEmpty()) }
            .map { (field, value) ->
                MetadataSuggestionEntity(
                    trackId = trackId,
                    field = field,
                    suggestedValue = value,
                    source = source,
                    confidence = metadataConfidence(field),
                    createdAtMillis = now
                )
            }
        if (suggestions.isNotEmpty()) dao.insertMetadataSuggestions(suggestions)
        if (externalId.isNotBlank() || externalUrl.isNotBlank()) {
            dao.upsertExternalMatch(
                ExternalMatchEntity(
                    trackId = trackId,
                    provider = source,
                    externalId = externalId.ifBlank { externalUrl },
                    url = externalUrl,
                    confidence = 0.86f,
                    createdAtMillis = now
                )
            )
        }
        applyEffectiveDiscoveryMetadata(dao.findTrack(trackId) ?: track)
        return suggestions.size
    }

    suspend fun acceptMetadataSuggestion(suggestionId: Long) {
        val suggestion = dao.findMetadataSuggestion(suggestionId) ?: return
        acceptMetadataSuggestionInternal(suggestion)
        refreshLibraryFacets()
        runLibraryDoctor()
    }

    suspend fun acceptSafeMetadataSuggestions(): Int {
        val suggestions = dao.getSafePendingMetadataSuggestions()
            .distinctBy { "${it.trackId}|${it.field}" }
        suggestions.forEach { acceptMetadataSuggestionInternal(it) }
        if (suggestions.isNotEmpty()) {
            refreshLibraryFacets()
            runLibraryDoctor()
        }
        return suggestions.size
    }

    private suspend fun acceptMetadataSuggestionInternal(suggestion: MetadataSuggestionEntity) {
        val existing = dao.findUserConfirmedMetadata(suggestion.trackId)
        val now = System.currentTimeMillis()
        val confirmed = (existing ?: UserConfirmedMetadataEntity(trackId = suggestion.trackId, updatedAtMillis = now)).let { value ->
            when (suggestion.field) {
                "title" -> value.copy(title = suggestion.suggestedValue, updatedAtMillis = now)
                "artist" -> value.copy(artist = suggestion.suggestedValue, updatedAtMillis = now)
                "album" -> value.copy(album = suggestion.suggestedValue, updatedAtMillis = now)
                "genre" -> value.copy(genre = suggestion.suggestedValue, updatedAtMillis = now)
                else -> value.copy(updatedAtMillis = now)
            }
        }
        dao.upsertUserConfirmedMetadata(confirmed)
        dao.markEnrichedMetadataFieldConfirmed(suggestion.trackId, suggestion.field)
        when (suggestion.field) {
            "title" -> dao.acceptSuggestedTitle(suggestion.trackId, suggestion.suggestedValue)
            "artist" -> dao.acceptSuggestedArtist(suggestion.trackId, suggestion.suggestedValue)
            "album" -> dao.acceptSuggestedAlbum(suggestion.trackId, suggestion.suggestedValue)
            "genre" -> dao.acceptSuggestedGenre(suggestion.trackId, suggestion.suggestedValue)
        }
        dao.updateMetadataSuggestionStatus(suggestion.id, "Accepted")
        dao.upsertMetadataWriteback(
            MetadataWritebackEntity(
                trackId = suggestion.trackId,
                status = "Pending",
                requestedAtMillis = now
            )
        )
    }

    suspend fun saveUserMetadata(input: TrackMetadataInput) {
        val track = dao.findTrack(input.trackId) ?: error("Track is no longer in the library")
        require(input.title.isNotBlank()) { "Title cannot be empty" }
        val now = System.currentTimeMillis()
        val existing = dao.findUserConfirmedMetadata(input.trackId)
        val cleanTags = input.discoveryTags
            .split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
        val confirmed = UserConfirmedMetadataEntity(
            id = existing?.id ?: 0,
            trackId = input.trackId,
            title = input.title.trim(),
            artist = input.artist.trim(),
            album = input.album.trim(),
            albumArtist = input.albumArtist.trim(),
            genre = input.genre.trim(),
            year = input.year?.takeIf { it in 1000..9999 },
            trackNumber = input.trackNumber?.takeIf { it > 0 },
            discNumber = input.discNumber?.takeIf { it > 0 },
            language = input.language.trim(),
            mood = input.mood.trim(),
            energy = input.energy?.coerceIn(0, 100),
            discoveryTags = cleanTags,
            updatedAtMillis = now
        )
        dao.upsertUserConfirmedMetadata(confirmed)
        dao.markAllEnrichedMetadataConfirmed(input.trackId)
        dao.updateConfirmedTrackMetadata(
            trackId = track.id,
            title = confirmed.title.orEmpty(),
            artist = confirmed.artist.orEmpty(),
            album = confirmed.album.orEmpty(),
            albumArtist = confirmed.albumArtist.orEmpty(),
            genre = confirmed.genre.orEmpty(),
            year = confirmed.year,
            trackNumber = confirmed.trackNumber,
            discNumber = confirmed.discNumber,
            language = confirmed.language.orEmpty(),
            mood = confirmed.mood.orEmpty(),
            energy = confirmed.energy,
            discoveryTags = confirmed.discoveryTags.orEmpty()
        )
        dao.upsertMetadataWriteback(
            MetadataWritebackEntity(
                trackId = input.trackId,
                status = "Pending",
                requestedAtMillis = now
            )
        )
        refreshLibraryFacets()
        runLibraryDoctor()
    }

    suspend fun ignoreMetadataSuggestion(suggestionId: Long) =
        dao.updateMetadataSuggestionStatus(suggestionId, "Ignored")

    suspend fun runLibraryDoctor(): LibraryHealth {
        val availableTracks = dao.getAllTracksOnce().filter { it.availability == "Available" }
        val issues = doctor.analyse(availableTracks)
        val now = System.currentTimeMillis()
        dao.deleteAnalysisResults()
        dao.insertAnalysisResults(
            issues.map { issue ->
                AnalysisResultEntity(
                    trackId = issue.trackId,
                    issueType = issue.type,
                    explanation = issue.explanation,
                    severity = issue.severity,
                    createdAtMillis = now
                )
            }
        )
        val issuesByTrack = issues.groupBy { it.trackId }
        availableTracks.forEach { track ->
            val status = when {
                issuesByTrack[track.id].orEmpty().any { it.type == "Possible duplicate" } -> "PossibleDuplicate"
                issuesByTrack[track.id].orEmpty().any { it.type.contains("Unknown", ignoreCase = true) || it.type.contains("Missing", ignoreCase = true) } -> "MissingMetadata"
                issuesByTrack.containsKey(track.id) -> "NeedsReview"
                else -> "Ready"
            }
            dao.updateTrackState(track.id, "Available", status)
        }
        val missing = issues.count { it.type.contains("Unknown", ignoreCase = true) || it.type.contains("Missing", ignoreCase = true) }
        val duplicates = issues.count { it.type == "Possible duplicate" }
        val issueCount = missing + duplicates
        return LibraryHealth(
            score = (100 - issueCount.coerceAtMost(100)).coerceIn(0, 100),
            issueCount = issueCount,
            duplicateCandidates = duplicates,
            missingMetadata = missing
        )
    }

    suspend fun refreshLibraryFacets() {
        val facets = LibraryFacetBuilder.build(dao.getAllTracksOnce())
        dao.replaceLibraryFacets(facets.albums, facets.artists, facets.folders)
        refreshBlacklistPreviewCounts()
    }

    private suspend fun scanRootInternal(
        root: MusicRootEntity,
        rules: List<FolderBlacklistRuleEntity>,
        scanJobId: Long,
        localEnrichedTrackIds: MutableSet<Long>,
        localEnrichmentEnabled: Boolean
    ): ScanProgress {
        dao.updateMusicRootScanStatus(root.id, "Scanning")
        return try {
            var finalProgress = ScanProgress(currentRoot = root.displayName, estimatedRemainingWork = "Queued")
            val existing = dao.getAllTracksOnce().associateBy { it.mediaKey }.toMutableMap()
            val seen = mutableSetOf<String>()
            var newTracks = 0
            var updatedTracks = 0
            scanner.scanAudio(rules, rootFilter = root.location, rootUri = root.uri).collect { result ->
                val persisted = persistScannedBatch(
                    result.tracks,
                    root.id,
                    existing,
                    scanJobId,
                    localEnrichedTrackIds,
                    localEnrichmentEnabled
                )
                seen += persisted.seenContentUris
                newTracks += persisted.newTracks
                updatedTracks += persisted.updatedTracks
                finalProgress = result.progress.copy(
                    currentRoot = root.displayName,
                    newTracks = newTracks,
                    updatedTracks = updatedTracks
                )
                _scanProgress.value = finalProgress
            }
            val missing = recordMissingTracks(
                tracks = existing.values.filter { it.rootId == root.id && it.contentUri !in seen },
                scanJobId = scanJobId
            )
            if (seen.isEmpty()) {
                dao.markAllRootTracksMissing(root.id)
            } else {
                dao.markUnseenRootTracksMissing(root.id, seen.toList())
            }
            finalProgress = finalProgress.copy(missingTracks = missing)
            _scanProgress.value = finalProgress.copy(estimatedRemainingWork = "Root scan complete")
            val tracksForRoot = dao.getAllTracksOnce().filter {
                it.rootId == root.id && it.availability == "Available"
            }
            dao.updateMusicRootScanSummary(
                rootId = root.id,
                scanStatus = "Complete",
                lastScanTimeMillis = System.currentTimeMillis(),
                indexedTrackCount = tracksForRoot.size,
                storageBytes = tracksForRoot.sumOf { it.sizeBytes }
            )
            finalProgress
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                dao.updateMusicRootScanStatus(
                    root.id,
                    if (error is CancellationException) "Paused" else "Failed"
                )
            }
            throw error
        }
    }

    private suspend fun persistScannedBatch(
        scanned: List<TrackEntity>,
        rootId: Long?,
        existingByMediaId: MutableMap<String, TrackEntity>,
        scanJobId: Long,
        localEnrichedTrackIds: MutableSet<Long>,
        localEnrichmentEnabled: Boolean
    ): PersistedBatch {
        if (scanned.isEmpty()) return PersistedBatch()
        var newTracks = 0
        var updatedTracks = 0
        val newIndexes = mutableSetOf<Int>()
        val merged = mutableListOf<TrackEntity>()
        val changeTypes = mutableListOf<String>()
        for ((index, candidate) in scanned.withIndex()) {
            val existing = existingByMediaId[candidate.mediaKey]
            if (existing == null) {
                if (candidate.availability == "Available") newTracks += 1
                newIndexes += index
                merged += candidate.copy(rootId = rootId)
                changeTypes += if (candidate.availability == "Excluded") "Excluded" else "New"
            } else {
                val changeType = when {
                    candidate.availability == "Excluded" && existing.availability != "Excluded" -> "Excluded"
                    candidate.availability == "Excluded" -> "Unchanged"
                    existing.relativePath != candidate.relativePath || existing.contentUri != candidate.contentUri -> "Moved"
                    existing.availability == "Excluded" -> "Restored"
                    existing.availability == "Missing" -> "Restored"
                    existing.hasFileChanges(candidate) -> "Modified"
                    else -> "Unchanged"
                }
                if (changeType in setOf("Modified", "Moved", "Restored")) updatedTracks += 1
                changeTypes += changeType
                val confirmed = dao.findUserConfirmedMetadata(existing.id)
                merged += candidate.copy(
                    id = existing.id,
                    rootId = rootId,
                    title = confirmed?.title ?: candidate.title,
                    artist = confirmed?.artist ?: candidate.artist,
                    album = confirmed?.album ?: candidate.album,
                    albumArtist = confirmed?.albumArtist ?: candidate.albumArtist,
                    genre = confirmed?.genre ?: candidate.genre,
                    year = confirmed?.year ?: candidate.year,
                    trackNumber = confirmed?.trackNumber ?: candidate.trackNumber,
                    discNumber = confirmed?.discNumber ?: candidate.discNumber,
                    language = confirmed?.language ?: existing.language,
                    mood = confirmed?.mood ?: existing.mood,
                    energy = confirmed?.energy ?: existing.energy,
                    discoveryTags = confirmed?.discoveryTags ?: existing.discoveryTags,
                    enhancementStatus = when {
                        candidate.availability == "Excluded" -> "Excluded"
                        existing.enhancementStatus == "Excluded" -> candidate.enhancementStatus
                        existing.enhancementStatus == "Ready" -> candidate.enhancementStatus
                        else -> existing.enhancementStatus
                    },
                    availability = candidate.availability
                )
            }
        }
        val ids = dao.upsertTracks(merged)
        val scanTime = System.currentTimeMillis()
        val scanChanges = mutableListOf<ScanChangeEntity>()
        merged.forEachIndexed { index, track ->
            var persisted = track.copy(id = ids[index])
            if (
                localEnrichmentEnabled &&
                persisted.availability == "Available" &&
                (persisted.id !in localEnrichedTrackIds || changeTypes[index] != "Unchanged")
            ) {
                persisted = refreshLocalEnrichment(persisted)
                localEnrichedTrackIds += persisted.id
            }
            existingByMediaId[track.mediaKey] = persisted
            dao.upsertTrackAvailability(
                TrackAvailabilityEntity(
                    trackId = ids[index],
                    state = track.availability,
                    firstMissingAtMillis = null,
                    lastCheckedAtMillis = scanTime
                )
            )
            if (changeTypes[index] != "Unchanged") {
                scanChanges += ScanChangeEntity(
                    scanJobId = scanJobId,
                    trackId = ids[index],
                    mediaStoreId = track.mediaStoreId,
                    changeType = changeTypes[index],
                    note = "${track.volumeName}:${track.relativePath}"
                )
            }
            if (index in newIndexes || dao.findOriginalMetadata(ids[index]) == null) {
                dao.insertOriginalMetadata(
                    OriginalMetadataEntity(
                        trackId = ids[index],
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        albumArtist = track.albumArtist,
                        genre = track.genre,
                        year = track.year,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
            }
        }
        if (scanChanges.isNotEmpty()) dao.insertScanChanges(scanChanges)
        return PersistedBatch(merged.map { it.contentUri }.toSet(), newTracks, updatedTracks)
    }

    private suspend fun refreshLocalEnrichment(track: TrackEntity): TrackEntity {
        val now = System.currentTimeMillis()
        val confirmed = dao.findUserConfirmedMetadata(track.id)
        val baseTrack = track.copy(
            language = confirmed?.language ?: track.language,
            mood = confirmed?.mood ?: track.mood,
            energy = confirmed?.energy ?: track.energy,
            discoveryTags = confirmed?.discoveryTags ?: track.discoveryTags
        )
        val inferred = LocalMetadataEnricher.infer(baseTrack)
        dao.deleteEnrichedMetadata(track.id, LocalMetadataEnricher.SOURCE)
        if (inferred.isNotEmpty()) {
            dao.insertEnrichedMetadata(
                inferred.map { value ->
                    EnrichedMetadataEntity(
                        trackId = track.id,
                        key = value.key,
                        value = value.value,
                        source = LocalMetadataEnricher.SOURCE,
                        confidence = value.confidence,
                        createdAtMillis = now,
                        confirmed = when (value.key) {
                            "mood" -> confirmed?.mood != null
                            "energy" -> confirmed?.energy != null
                            "language" -> confirmed?.language != null
                            "era" -> confirmed?.year != null
                            "discoveryTags" -> confirmed?.discoveryTags != null
                            else -> confirmed?.discoveryTags?.contains(value.value, ignoreCase = true) == true
                        }
                    )
                }
            )
        }
        return applyEffectiveDiscoveryMetadata(baseTrack)
    }

    private suspend fun applyEffectiveDiscoveryMetadata(track: TrackEntity): TrackEntity {
        val confirmed = dao.findUserConfirmedMetadata(track.id)
        val enriched = dao.getEnrichedMetadataForTrack(track.id)
        fun bestValue(key: String): String? = enriched
            .filter { it.key.equals(key, ignoreCase = true) }
            .maxByOrNull { it.confidence }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val enrichedTags = enriched
            .filter { it.key.equals("tags", true) || it.key.equals("discoveryTags", true) }
            .flatMap { it.value.split(',', ';', '|') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
        val language = confirmed?.language ?: bestValue("language") ?: track.language
        val mood = confirmed?.mood ?: bestValue("mood") ?: track.mood
        val energy = confirmed?.energy ?: bestValue("energy")?.toIntOrNull() ?: track.energy
        val discoveryTags = confirmed?.discoveryTags ?: enrichedTags.ifBlank { track.discoveryTags }
        dao.updateEffectiveDiscoveryMetadata(
            trackId = track.id,
            language = language,
            mood = mood,
            energy = energy,
            discoveryTags = discoveryTags
        )
        return track.copy(
            language = language,
            mood = mood,
            energy = energy,
            discoveryTags = discoveryTags
        )
    }

    private suspend fun reconcileExclusions() {
        val enabledRules = dao.getEnabledBlacklistRules()
        val rootsById = dao.observeMusicRootsOnce().associateBy { it.id }
        val now = System.currentTimeMillis()
        dao.getAllTracksOnce().forEach { track ->
            val rootExcluded = track.rootId?.let { rootsById[it]?.included == false } == true
            val shouldExclude = rootExcluded || BlacklistMatcher.isExcluded(track.relativePath, enabledRules)
            when {
                shouldExclude && track.availability == "Available" -> {
                    dao.updateTrackState(track.id, "Excluded", "Excluded")
                    dao.upsertTrackAvailability(
                        TrackAvailabilityEntity(
                            trackId = track.id,
                            state = "Excluded",
                            lastCheckedAtMillis = now
                        )
                    )
                }
                !shouldExclude && track.availability == "Excluded" -> {
                    val status = if (track.title.isBlank() || track.artist.isBlank() || track.album.isBlank()) {
                        "MissingMetadata"
                    } else {
                        "Ready"
                    }
                    dao.updateTrackState(track.id, "Available", status)
                    dao.upsertTrackAvailability(
                        TrackAvailabilityEntity(
                            trackId = track.id,
                            state = "Available",
                            lastCheckedAtMillis = now
                        )
                    )
                }
            }
        }
    }

    private suspend fun recordMissingTracks(tracks: List<TrackEntity>, scanJobId: Long): Int {
        val newlyMissing = tracks.filter { it.availability != "Missing" }
        val now = System.currentTimeMillis()
        newlyMissing.forEach { track ->
            dao.upsertTrackAvailability(
                TrackAvailabilityEntity(
                    trackId = track.id,
                    state = "Missing",
                    firstMissingAtMillis = now,
                    lastCheckedAtMillis = now
                )
            )
        }
        if (newlyMissing.isNotEmpty()) {
            dao.insertScanChanges(
                newlyMissing.map { track ->
                    ScanChangeEntity(
                        scanJobId = scanJobId,
                        trackId = track.id,
                        mediaStoreId = track.mediaStoreId,
                        changeType = "Missing",
                        note = "${track.volumeName}:${track.relativePath}"
                    )
                }
            )
        }
        return newlyMissing.size
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

private fun metadataConfidence(field: String): Float = when (field) {
    "title", "artist" -> 0.9f
    "album" -> 0.82f
    "genre" -> 0.68f
    else -> 0.72f
}

private fun normalizeMetadata(value: String): String = value
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), "")

private data class PersistedBatch(
    val seenContentUris: Set<String> = emptySet(),
    val newTracks: Int = 0,
    val updatedTracks: Int = 0
)

private val TrackEntity.mediaKey: String
    get() = "$volumeName|$mediaStoreId"

private fun TrackEntity.hasFileChanges(other: TrackEntity): Boolean =
    contentUri != other.contentUri ||
        relativePath != other.relativePath ||
        fileName != other.fileName ||
        sizeBytes != other.sizeBytes ||
        durationMs != other.durationMs ||
        dateModifiedSeconds != other.dateModifiedSeconds ||
        mimeType != other.mimeType

private fun PlaylistRules.toStorageJson(): String = JSONObject()
    .put("targetDurationMinutes", targetDurationMinutes)
    .put("includedGenres", JSONArray(includedGenres))
    .put("excludedGenres", JSONArray(excludedGenres))
    .put("languages", JSONArray(languages))
    .put("folders", JSONArray(folders))
    .put("fileFormats", JSONArray(fileFormats))
    .put("preferUnplayed", preferUnplayed)
    .put("avoidArtistRepetitions", avoidArtistRepetitions)
    .put("energyMinimum", energyRange?.first)
    .put("energyMaximum", energyRange?.last)
    .put("familiarityLevel", familiarityLevel)
    .put("addedWithinDays", addedWithinDays)
    .toString()
