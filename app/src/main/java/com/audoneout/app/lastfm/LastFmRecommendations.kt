package com.audoneout.app.lastfm

import com.audoneout.app.data.TrackEntity
import javax.inject.Inject
import javax.inject.Singleton

data class LastFmRecommendation(
    val track: LastFmTrack,
    val localTrack: TrackEntity?,
    val reason: String,
    val score: Double
)

data class LastFmDiscoveryResult(
    val profile: LastFmProfile,
    val recommendations: List<LastFmRecommendation>,
    val localMatchCount: Int,
    val localTasteSeeds: List<TrackEntity>,
    val recentScrobbleCount: Int
)

@Singleton
class LastFmRecommendationService @Inject constructor(
    private val client: LastFmClient
) {
    suspend fun discover(
        username: String,
        apiKey: String,
        localTracks: List<TrackEntity>
    ): LastFmDiscoveryResult {
        val profile = client.getProfile(username, apiKey)
        val topTracks = client.getTopTracks(username, apiKey)
        val lovedTracks = runCatching { client.getLovedTracks(username, apiKey) }.getOrDefault(emptyList())
        val recentTracks = runCatching { client.getRecentTracks(username, apiKey) }.getOrDefault(emptyList())
        val seeds = (lovedTracks.take(3) + recentTracks.take(4) + topTracks.take(4))
            .distinctBy { trackKey(it.artist, it.name) }
            .take(6)
        val seedKeys = seeds.mapTo(mutableSetOf()) { trackKey(it.artist, it.name) }
        val candidates = linkedMapOf<String, RankedCandidate>()
        val localByKey = localTracks.associateBy { trackKey(it.artist, it.title) }
        val localTasteSeeds = (lovedTracks + recentTracks + topTracks)
            .mapNotNull { track ->
                localByKey[trackKey(track.artist, track.name)] ?: findLooseLocalMatch(localTracks, track)
            }
            .distinctBy { it.id }
            .take(16)

        seeds.forEachIndexed { index, seed ->
            val seedWeight = 1.0 - index * 0.08
            val related = runCatching {
                client.getSimilarTracks(seed.artist, seed.name, apiKey, limit = 24)
            }.getOrDefault(emptyList())
            related.forEach { candidate ->
                val key = trackKey(candidate.artist, candidate.name)
                if (key in seedKeys) return@forEach
                val score = normalizedMatch(candidate.match) * seedWeight
                val existing = candidates[key]
                if (existing == null) {
                    candidates[key] = RankedCandidate(candidate, score, "Similar to ${seed.artist} - ${seed.name}")
                } else {
                    candidates[key] = existing.copy(score = existing.score + score * 0.45)
                }
            }
        }

        if (candidates.isEmpty()) {
            (lovedTracks + recentTracks + topTracks).distinctBy { trackKey(it.artist, it.name) }.forEachIndexed { index, track ->
                candidates[trackKey(track.artist, track.name)] = RankedCandidate(
                    track = track,
                    score = 1.0 - index * 0.01,
                    reason = "From your Last.fm listening history"
                )
            }
        }

        val recommendations = candidates.values
            .sortedByDescending { it.score }
            .take(40)
            .map { candidate ->
                val local = localByKey[trackKey(candidate.track.artist, candidate.track.name)]
                    ?: findLooseLocalMatch(localTracks, candidate.track)
                LastFmRecommendation(candidate.track, local, candidate.reason, candidate.score)
            }
        return LastFmDiscoveryResult(
            profile = profile,
            recommendations = recommendations,
            localMatchCount = recommendations.count { it.localTrack != null },
            localTasteSeeds = localTasteSeeds,
            recentScrobbleCount = recentTracks.size
        )
    }

    private data class RankedCandidate(val track: LastFmTrack, val score: Double, val reason: String)
}

object LastFmLinks {
    const val apiAccountUrl: String = "https://www.last.fm/api/account/create"

    fun youtubeMusicSearch(artist: String, title: String): String =
        "https://music.youtube.com/search?q=${urlPart("$artist $title")}" 

    fun profile(username: String): String = "https://www.last.fm/user/${urlPart(username)}"

    private fun urlPart(value: String): String = java.net.URLEncoder
        .encode(value, Charsets.UTF_8.name())
        .replace("+", "%20")
}

internal fun trackKey(artist: String, title: String): String = "${normalizeMusicText(artist)}|${normalizeMusicText(title)}"

private fun normalizeMusicText(value: String): String = value
    .lowercase()
    .replace("&", "and")
    .replace(Regex("\\b(feat|featuring|ft)\\.?\\s+.*$"), "")
    .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), "")

private fun normalizedMatch(value: Double): Double = when {
    value <= 0.0 -> 0.15
    value > 1.0 -> (value / 100.0).coerceIn(0.0, 1.0)
    else -> value
}

private fun findLooseLocalMatch(localTracks: List<TrackEntity>, candidate: LastFmTrack): TrackEntity? {
    val title = normalizeMusicText(candidate.name)
    val artist = normalizeMusicText(candidate.artist)
    return localTracks.firstOrNull { local ->
        normalizeMusicText(local.title) == title && (
            normalizeMusicText(local.artist).contains(artist) || artist.contains(normalizeMusicText(local.artist))
        )
    }
}
