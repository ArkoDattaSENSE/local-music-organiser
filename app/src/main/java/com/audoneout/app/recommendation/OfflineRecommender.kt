package com.audoneout.app.recommendation

import com.audoneout.app.data.ListeningEventEntity
import com.audoneout.app.data.TrackEntity
import javax.inject.Inject
import kotlin.math.ln

data class TrackRecommendation(
    val track: TrackEntity,
    val score: Double,
    val reasons: List<String>
)

class OfflineRecommender @Inject constructor() {
    fun recommend(
        library: List<TrackEntity>,
        seeds: List<TrackEntity>,
        favorites: List<TrackEntity> = emptyList(),
        events: List<ListeningEventEntity> = emptyList(),
        limit: Int = 40,
        onlineSeedIds: Set<Long> = emptySet(),
        explorationSeed: Long = 0L
    ): List<TrackRecommendation> {
        if (library.isEmpty()) return emptyList()
        val seedSet = seeds.map { it.id }.toSet()
        val playCounts = events.filter { it.eventType == "Listened" || it.eventType == "Play" }.groupingBy { it.trackId }.eachCount()
        val skipCounts = events.filter { it.eventType == "Skip" }.groupingBy { it.trackId }.eachCount()
        val favoriteIds = favorites.map { it.id }.toSet()
        val effectiveSeeds = (seeds + favorites.take(8)).distinctBy { it.id }
        val ranked = library.asSequence()
            .filter { it.availability == "Available" && it.enhancementStatus != "Excluded" && it.id !in seedSet }
            .map { track ->
                score(
                    track = track,
                    seeds = effectiveSeeds,
                    favoriteIds = favoriteIds,
                    playCounts = playCounts,
                    skipCounts = skipCounts,
                    onlineSeedIds = onlineSeedIds,
                    explorationSeed = explorationSeed
                )
            }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .toList()
        return diversify(ranked, limit)
    }

    private fun score(
        track: TrackEntity,
        seeds: List<TrackEntity>,
        favoriteIds: Set<Long>,
        playCounts: Map<Long, Int>,
        skipCounts: Map<Long, Int>,
        onlineSeedIds: Set<Long>,
        explorationSeed: Long
    ): TrackRecommendation {
        var score = 1.0
        val reasons = mutableListOf<String>()
        val bestSeed = seeds.maxByOrNull { similarity(track, it) }
        val similarity = bestSeed?.let { similarity(track, it) } ?: 0.0
        score += similarity
        if (similarity >= 3.0 && bestSeed != null) {
            reasons += "Related to ${bestSeed.title}"
        }
        if (bestSeed?.id?.let { it in onlineSeedIds } == true) reasons += "Matches your Last.fm taste"
        if (bestSeed != null && track.genre.matchesNonBlank(bestSeed.genre)) reasons += "Shared genre"
        if (bestSeed != null && track.language.matchesNonBlank(bestSeed.language)) reasons += "Shared language"
        if (bestSeed != null && track.mood.matchesNonBlank(bestSeed.mood)) reasons += "Similar mood"
        if (track.id in favoriteIds) {
            score += 1.5
            reasons += "One of your favorites"
        }
        val plays = playCounts[track.id] ?: 0
        val skips = skipCounts[track.id] ?: 0
        if (plays == 0) {
            score += 1.2
            reasons += "Not played yet"
        } else {
            score += ln(plays + 1.0) * 0.35
        }
        if (skips > 0) score -= skips.coerceAtMost(5) * 0.65
        if (track.format.equals("FLAC", true) || track.mimeType.contains("flac", true)) {
            score += 0.3
            reasons += "Lossless local file"
        }
        val metadataSignals = listOf(track.genre, track.language, track.mood, track.discoveryTags)
            .count { it.isNotBlank() } + listOf(track.year, track.energy).count { it != null }
        score += metadataSignals * 0.12
        if (metadataSignals >= 4) reasons += "Rich local metadata match"
        val exploration = explorationValue(track.id, explorationSeed)
        score += exploration * 0.85
        if (exploration >= 0.82) reasons += "Exploration pick"
        if (reasons.isEmpty()) reasons += "Fresh pick from your local library"
        return TrackRecommendation(track, score, reasons.distinct().take(3))
    }

    private fun similarity(track: TrackEntity, seed: TrackEntity): Double {
        var score = 0.0
        if (track.artist.matchesNonBlank(seed.artist)) score += 4.0
        if (track.album.matchesNonBlank(seed.album)) score += 2.5
        if (track.genre.matchesNonBlank(seed.genre)) score += 2.5
        if (track.language.matchesNonBlank(seed.language)) score += 1.5
        if (track.mood.matchesNonBlank(seed.mood)) score += 1.5
        if (track.folder.matchesNonBlank(seed.folder)) score += 0.6
        if (track.format.matchesNonBlank(seed.format)) score += 0.4
        if (track.year != null && seed.year != null && track.year / 10 == seed.year / 10) score += 0.6
        val sharedTags = track.discoveryTags.tags().intersect(seed.discoveryTags.tags()).size
        score += sharedTags.coerceAtMost(4) * 0.7
        val bothEnergy = track.energy?.let { a -> seed.energy?.let { b -> a to b } }
        if (bothEnergy != null) score += (1.0 - kotlin.math.abs(bothEnergy.first - bothEnergy.second) / 100.0).coerceAtLeast(0.0)
        return score
    }

    private fun diversify(
        ranked: List<TrackRecommendation>,
        limit: Int
    ): List<TrackRecommendation> {
        val remaining = ranked.toMutableList()
        val selected = mutableListOf<TrackRecommendation>()
        val artistCounts = mutableMapOf<String, Int>()
        val albumCounts = mutableMapOf<String, Int>()
        val genreCounts = mutableMapOf<String, Int>()
        while (remaining.isNotEmpty() && selected.size < limit) {
            val best = remaining.maxByOrNull { candidate ->
                val artistPenalty = (artistCounts[candidate.track.artist.normalized()] ?: 0) * 1.6
                val albumPenalty = (albumCounts[candidate.track.album.normalized()] ?: 0) * 0.75
                val genrePenalty = (genreCounts[candidate.track.genre.normalized()] ?: 0) * 0.08
                candidate.score - artistPenalty - albumPenalty - genrePenalty
            } ?: break
            remaining.remove(best)
            selected += best
            best.track.artist.normalized().takeIf { it.isNotBlank() }?.let {
                artistCounts[it] = (artistCounts[it] ?: 0) + 1
            }
            best.track.album.normalized().takeIf { it.isNotBlank() }?.let {
                albumCounts[it] = (albumCounts[it] ?: 0) + 1
            }
            best.track.genre.normalized().takeIf { it.isNotBlank() }?.let {
                genreCounts[it] = (genreCounts[it] ?: 0) + 1
            }
        }
        return selected
    }
}

private fun String.matchesNonBlank(other: String): Boolean =
    isNotBlank() && other.isNotBlank() && normalized() == other.normalized()

private fun String.normalized(): String = lowercase().trim().replace(Regex("\\s+"), " ")

private fun String.tags(): Set<String> =
    split(',', ';', '|').map { it.normalized() }.filter { it.isNotBlank() }.toSet()

private fun explorationValue(trackId: Long, seed: Long): Double {
    val mixed = (trackId * 1_103_515_245L + seed * 12_345L) xor
        ((trackId + seed) shl 21)
    return (mixed ushr 16 and 0xFFFF).toDouble() / 0xFFFF
}
