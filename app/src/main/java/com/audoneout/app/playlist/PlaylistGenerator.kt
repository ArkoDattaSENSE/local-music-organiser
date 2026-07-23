package com.audoneout.app.playlist

import com.audoneout.app.data.ListeningEventEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.domain.PlaylistCandidate
import com.audoneout.app.domain.PlaylistRules
import com.audoneout.app.domain.Track
import javax.inject.Inject

class PlaylistGenerator @Inject constructor() {
    fun generate(
        tracks: List<TrackEntity>,
        rules: PlaylistRules,
        favoriteIds: Set<Long> = emptySet(),
        events: List<ListeningEventEntity> = emptyList(),
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): List<PlaylistCandidate> {
        val playCounts = events
            .filter { it.eventType == "Listened" || it.eventType == "Play" }
            .groupingBy { it.trackId }
            .eachCount()
        val skipCounts = events
            .filter { it.eventType == "Skip" }
            .groupingBy { it.trackId }
            .eachCount()
        val minimumDateAddedSeconds = rules.addedWithinDays?.let { days ->
            nowSeconds - days * 86_400L
        }
        val remaining = tracks
            .map { track ->
                score(
                    track = track,
                    rules = rules,
                    favorite = track.id in favoriteIds,
                    playCount = playCounts[track.id] ?: 0,
                    skipCount = skipCounts[track.id] ?: 0,
                    minimumDateAddedSeconds = minimumDateAddedSeconds
                )
            }
            .filter { it.score > 0.0 }
            .toMutableList()
        val selected = mutableListOf<PlaylistCandidate>()
        val artistCounts = mutableMapOf<String, Int>()
        val albumCounts = mutableMapOf<String, Int>()
        val targetMs = rules.targetDurationMinutes?.let { it * 60_000L } ?: Long.MAX_VALUE
        val maximumTracks = if (targetMs == Long.MAX_VALUE) 50 else Int.MAX_VALUE
        var totalMs = 0L
        while (remaining.isNotEmpty() && totalMs < targetMs && selected.size < maximumTracks) {
            if (rules.avoidArtistRepetitions) {
                remaining.removeAll { candidate ->
                    (artistCounts[candidate.track.artist.artistKey(candidate.track.libraryId)] ?: 0) > 0
                }
            }
            val candidate = remaining.maxByOrNull { value ->
                val artistPenalty = (artistCounts[value.track.artist.artistKey(value.track.libraryId)] ?: 0) * 1.4
                val albumPenalty = (albumCounts[value.track.album.normalized()] ?: 0) * 0.65
                value.score - artistPenalty - albumPenalty
            } ?: break
            remaining.remove(candidate)
            val artistKey = candidate.track.artist.artistKey(candidate.track.libraryId)
            val albumKey = candidate.track.album.normalized()
            val albumRepeated = albumKey.isNotBlank() && (albumCounts[albumKey] ?: 0) > 0
            selected += candidate.copy(
                reasons = (candidate.reasons + if (albumRepeated) "Balanced against album repetition" else "")
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(4)
            )
            artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
            if (albumKey.isNotBlank()) albumCounts[albumKey] = (albumCounts[albumKey] ?: 0) + 1
            totalMs += candidate.track.durationMs
        }
        return selected
    }

    fun score(
        track: TrackEntity,
        rules: PlaylistRules,
        usedArtists: Set<String> = emptySet(),
        favorite: Boolean = false,
        playCount: Int = 0,
        skipCount: Int = 0,
        minimumDateAddedSeconds: Long? = null
    ): PlaylistCandidate {
        val reasons = mutableListOf<String>()
        var score = 1.0
        if (track.availability != "Available" || track.enhancementStatus == "Excluded") {
            return track.rejected("Unavailable or excluded")
        }
        if (rules.fileFormats.isNotEmpty()) {
            if (rules.fileFormats.any { it.equals(track.format, ignoreCase = true) || track.mimeType.contains(it, ignoreCase = true) }) {
                score += 3.0
                reasons += "Matches requested file format"
            } else {
                return track.rejected("Does not match requested file format")
            }
        }
        if (rules.excludedGenres.any { track.matchesTagOrValue(track.genre, it) }) {
            return track.rejected("Matches an excluded genre")
        }
        if (rules.includedGenres.isNotEmpty()) {
            if (rules.includedGenres.any { track.matchesTagOrValue(track.genre, it) }) {
                score += 2.0
                reasons += "Matches requested genre or discovery tag"
            } else {
                return track.rejected("Does not match requested genre")
            }
        }
        if (rules.languages.isNotEmpty()) {
            if (rules.languages.any { track.matchesTagOrValue(track.language, it) }) {
                score += 1.5
                reasons += "Matches requested language"
            } else {
                return track.rejected("Does not match requested language")
            }
        }
        if (rules.folders.isNotEmpty()) {
            if (rules.folders.any { track.relativePath.contains(it, ignoreCase = true) }) {
                score += 1.5
                reasons += "Comes from requested folder"
            } else {
                return track.rejected("Outside requested folders")
            }
        }
        if (rules.energyRange != null) {
            if (track.energy != null && track.energy in rules.energyRange) {
                score += 1.0
                reasons += "Energy fits prompt"
            } else {
                return track.rejected("Energy does not fit prompt")
            }
        }
        if (minimumDateAddedSeconds != null) {
            if (track.dateAddedSeconds >= minimumDateAddedSeconds) {
                score += 1.2
                reasons += "Recently added"
            } else {
                return track.rejected("Older than the requested window")
            }
        }
        if (rules.avoidArtistRepetitions && track.artist.normalized() in usedArtists.map { it.normalized() }) {
            return track.rejected("Artist already used")
        }
        if (favorite) {
            score += 1.2
            reasons += "Speed Dial favorite"
        }
        if (rules.preferUnplayed || rules.familiarityLevel == "discovery") {
            if (playCount == 0) {
                score += 1.4
                reasons += "Underplayed discovery"
            } else {
                score -= playCount.coerceAtMost(8) * 0.12
            }
        } else if (rules.familiarityLevel == "familiar" && (favorite || playCount > 0)) {
            score += 1.0
            reasons += "Familiar library pick"
        }
        score -= skipCount.coerceAtMost(5) * 0.55
        score += deterministicJitter(track.id, rules.prompt) * 0.2
        if (reasons.isEmpty()) reasons += "Balanced local-library candidate"
        return PlaylistCandidate(track.toDomain(), score, reasons)
    }

    private fun TrackEntity.toDomain() = Track(
        libraryId = id,
        mediaStoreId = mediaStoreId,
        volumeName = volumeName,
        contentUri = contentUri,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        folder = folder,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateAddedSeconds = dateAddedSeconds,
        albumArtUri = albumArtUri,
        relativePath = relativePath,
        dateModifiedSeconds = dateModifiedSeconds,
        rootId = rootId,
        format = format,
        albumArtist = albumArtist,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitrate = bitrate,
        sampleRate = sampleRate,
        bitDepth = bitDepth
    )
}

private fun TrackEntity.rejected(reason: String): PlaylistCandidate =
    PlaylistCandidate(toPlaylistTrack(), 0.0, listOf(reason))

private fun TrackEntity.toPlaylistTrack() = Track(
    libraryId = id,
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
    contentUri = contentUri,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    folder = folder,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateAddedSeconds = dateAddedSeconds,
    albumArtUri = albumArtUri,
    relativePath = relativePath,
    dateModifiedSeconds = dateModifiedSeconds,
    rootId = rootId,
    format = format,
    albumArtist = albumArtist,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    genre = genre,
    bitrate = bitrate,
    sampleRate = sampleRate,
    bitDepth = bitDepth
)

private fun TrackEntity.matchesTagOrValue(value: String, requested: String): Boolean =
    value.contains(requested, ignoreCase = true) ||
        discoveryTags.split(',', ';', '|').any { it.trim().contains(requested, ignoreCase = true) }

private fun String.normalized(): String = trim().lowercase().replace(Regex("\\s+"), " ")

private fun String.artistKey(trackId: Long): String = normalized().ifBlank { "unknown-$trackId" }

private fun deterministicJitter(trackId: Long, prompt: String): Double {
    val mixed = trackId * 1_103_515_245L + prompt.hashCode().toLong() * 12_345L
    return (mixed ushr 16 and 0xFFFF).toDouble() / 0xFFFF
}
