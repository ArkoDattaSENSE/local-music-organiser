package com.audoneout.app.doctor

import com.audoneout.app.data.TrackEntity
import com.audoneout.app.domain.AnalysisIssue
import javax.inject.Inject

class LibraryDoctor @Inject constructor() {
    fun analyse(tracks: List<TrackEntity>): List<AnalysisIssue> {
        val issues = mutableListOf<AnalysisIssue>()
        tracks.forEach { track ->
            if (track.title.isBlank() || track.title.equals(track.fileName.substringBeforeLast('.'), ignoreCase = true)) {
                issues += AnalysisIssue(track.id, "Missing or weak title", "The filename appears to be standing in for a real title.")
            }
            if (track.artist.isBlank() || track.artist.equals("<unknown>", ignoreCase = true)) {
                issues += AnalysisIssue(track.id, "Unknown artist", "Artist metadata is missing or unknown.")
            }
            if (track.album.isBlank() || track.album.equals("<unknown>", ignoreCase = true)) {
                issues += AnalysisIssue(track.id, "Unknown album", "Album metadata is missing or unknown.")
            }
            if (track.albumArtUri == null) {
                issues += AnalysisIssue(track.id, "Missing artwork", "No album-art URI was found for this track.", severity = "info")
            }
            if (track.durationMs <= 0L || track.sizeBytes <= 0L) {
                issues += AnalysisIssue(track.id, "Broken or inaccessible file", "Duration or file size could not be read.", severity = "warning")
            }
        }
        duplicateGroups(tracks).forEach { group ->
            group.forEach { track ->
                issues += AnalysisIssue(track.id, "Possible duplicate", "Another track has matching title, artist, album, duration, format, and size.")
            }
        }
        return issues
    }

    fun duplicateGroups(tracks: List<TrackEntity>): List<List<TrackEntity>> =
        tracks.groupBy { track ->
            listOf(
                normalize(track.title),
                normalize(track.artist),
                normalize(track.album),
                track.durationMs / 1000,
                track.sizeBytes,
                track.format.lowercase()
            ).joinToString("|")
        }.values.filter { it.size > 1 }

    fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}

