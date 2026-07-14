package com.audoneout.app.playlist

import com.audoneout.app.data.TrackEntity
import com.audoneout.app.domain.PlaylistCandidate
import com.audoneout.app.domain.PlaylistRules
import com.audoneout.app.domain.Track
import javax.inject.Inject

class PlaylistGenerator @Inject constructor() {
    fun generate(tracks: List<TrackEntity>, rules: PlaylistRules): List<PlaylistCandidate> {
        val selected = mutableListOf<PlaylistCandidate>()
        val usedArtists = mutableSetOf<String>()
        val targetMs = rules.targetDurationMinutes?.let { it * 60_000L } ?: Long.MAX_VALUE
        var totalMs = 0L
        tracks.asSequence()
            .map { score(it, rules, usedArtists) }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .forEach { candidate ->
                if (totalMs >= targetMs) return@forEach
                if (rules.avoidArtistRepetitions && candidate.track.artist.lowercase() in usedArtists) return@forEach
                selected += candidate
                usedArtists += candidate.track.artist.lowercase()
                totalMs += candidate.track.durationMs
            }
        return selected
    }

    fun score(track: TrackEntity, rules: PlaylistRules, usedArtists: Set<String> = emptySet()): PlaylistCandidate {
        val reasons = mutableListOf<String>()
        var score = 1.0
        if (rules.fileFormats.isNotEmpty()) {
            if (rules.fileFormats.any { it.equals(track.format, ignoreCase = true) || track.mimeType.contains(it, ignoreCase = true) }) {
                score += 3.0
                reasons += "Matches requested file format"
            } else {
                score -= 3.0
            }
        }
        if (rules.includedGenres.isNotEmpty()) {
            if (rules.includedGenres.any { track.genre.contains(it, ignoreCase = true) || track.discoveryTags.contains(it, ignoreCase = true) }) {
                score += 2.0
                reasons += "Matches requested genre or discovery tag"
            } else {
                score -= 1.0
            }
        }
        if (rules.languages.isNotEmpty()) {
            if (rules.languages.any { track.language.contains(it, ignoreCase = true) || track.discoveryTags.contains(it, ignoreCase = true) }) {
                score += 1.5
                reasons += "Matches requested language"
            }
        }
        if (rules.folders.isNotEmpty() && rules.folders.any { track.relativePath.contains(it, ignoreCase = true) }) {
            score += 1.5
            reasons += "Comes from requested folder"
        }
        if (rules.energyRange != null && track.energy != null && track.energy in rules.energyRange) {
            score += 1.0
            reasons += "Energy fits prompt"
        }
        if (rules.avoidArtistRepetitions && track.artist.lowercase() in usedArtists) {
            score -= 10.0
        }
        if (track.availability != "Available" || track.enhancementStatus == "Excluded") {
            score = 0.0
        }
        if (reasons.isEmpty()) reasons += "Balanced local-library candidate"
        return PlaylistCandidate(track.toDomain(), score, reasons)
    }

    private fun TrackEntity.toDomain() = Track(
        mediaStoreId = mediaStoreId,
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

