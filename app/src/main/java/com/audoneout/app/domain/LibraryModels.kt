package com.audoneout.app.domain

data class Track(
    val mediaStoreId: Long,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folder: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val albumArtUri: String?,
    val relativePath: String,
    val dateModifiedSeconds: Long,
    val rootId: Long? = null,
    val format: String = mimeType.substringAfterLast('/', missingDelimiterValue = mimeType).uppercase(),
    val albumArtist: String = "",
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String = "",
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null
)

data class ScanProgress(
    val currentRoot: String = "",
    val currentFolder: String = "",
    val tracksFound: Int = 0,
    val newTracks: Int = 0,
    val updatedTracks: Int = 0,
    val excludedTracks: Int = 0,
    val failedTracks: Int = 0,
    val estimatedRemainingWork: String = "Idle",
    val cancellable: Boolean = true
)

data class PlaylistRules(
    val prompt: String = "",
    val targetDurationMinutes: Int? = null,
    val includedGenres: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    val fileFormats: List<String> = emptyList(),
    val preferUnplayed: Boolean = false,
    val avoidArtistRepetitions: Boolean = false,
    val energyRange: IntRange? = null,
    val familiarityLevel: String? = null
)

data class PlaylistCandidate(
    val track: Track,
    val score: Double,
    val reasons: List<String>
)

data class AnalysisIssue(
    val trackId: Long,
    val type: String,
    val explanation: String,
    val severity: String = "review"
)

data class LibraryHealth(
    val score: Int,
    val issueCount: Int,
    val duplicateCandidates: Int,
    val missingMetadata: Int
)

enum class AvailabilityState {
    Available,
    Missing,
    Restored,
    Excluded
}

enum class EnhancementStatus {
    Ready,
    NeedsReview,
    MissingMetadata,
    PossibleDuplicate,
    LowConfidence,
    Excluded,
    Failed
}

