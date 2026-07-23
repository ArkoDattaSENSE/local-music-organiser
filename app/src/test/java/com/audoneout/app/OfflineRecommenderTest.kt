package com.audoneout.app

import com.audoneout.app.data.ListeningEventEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.recommendation.OfflineRecommender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRecommenderTest {
    private val recommender = OfflineRecommender()

    @Test
    fun seedTrackIsExcludedAndMetadataSimilarityWins() {
        val seed = track(1, "Seed", "Artist A", genre = "Rock", language = "Bengali")
        val related = track(2, "Related", "Artist B", genre = "Rock", language = "Bengali")
        val unrelated = track(3, "Unrelated", "Artist C", genre = "Jazz", language = "English")

        val result = recommender.recommend(listOf(seed, unrelated, related), listOf(seed))

        assertFalse(result.any { it.track.id == seed.id })
        assertEquals(related.id, result.first().track.id)
        assertTrue(result.first().reasons.any { it.contains("genre", ignoreCase = true) })
    }

    @Test
    fun repeatedSkipsReduceRecommendationScore() {
        val seed = track(1, "Seed", "Artist A", genre = "Rock")
        val skipped = track(2, "Skipped", "Artist B", genre = "Rock")
        val fresh = track(3, "Fresh", "Artist C", genre = "Rock")
        val events = (1..5).map { index ->
            ListeningEventEntity(
                id = index.toLong(),
                trackId = skipped.id,
                eventType = "Skip",
                occurredAtMillis = index.toLong()
            )
        }

        val result = recommender.recommend(listOf(seed, skipped, fresh), listOf(seed), events = events)

        assertTrue(result.indexOfFirst { it.track.id == fresh.id } < result.indexOfFirst { it.track.id == skipped.id })
    }

    @Test
    fun localLastFmSeedIsExplained() {
        val lastFmSeed = track(1, "Taste seed", "Artist A", genre = "Dream pop")
        val related = track(2, "Related", "Artist B", genre = "Dream pop")

        val result = recommender.recommend(
            library = listOf(lastFmSeed, related),
            seeds = listOf(lastFmSeed),
            onlineSeedIds = setOf(lastFmSeed.id)
        )

        assertTrue(result.single().reasons.any { it.contains("Last.fm", ignoreCase = true) })
    }

    @Test
    fun discoveryTagsAndEraImproveSimilarity() {
        val seed = track(1, "Seed", "Artist A", year = 1994, discoveryTags = "night, dream pop")
        val related = track(2, "Related", "Artist B", year = 1998, discoveryTags = "dream pop, hazy")
        val unrelated = track(3, "Unrelated", "Artist C", year = 2018, discoveryTags = "workout")

        val result = recommender.recommend(listOf(seed, unrelated, related), listOf(seed))

        assertEquals(related.id, result.first().track.id)
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        genre: String = "",
        language: String = "",
        year: Int? = null,
        discoveryTags: String = ""
    ) = TrackEntity(
        id = id,
        mediaStoreId = id,
        contentUri = "content://track/$id",
        title = title,
        artist = artist,
        album = "Album",
        durationMs = 180_000,
        folder = "Music",
        fileName = "$title.flac",
        mimeType = "audio/flac",
        sizeBytes = 10_000_000,
        dateAddedSeconds = id,
        dateModifiedSeconds = id,
        relativePath = "Music/",
        genre = genre,
        language = language,
        year = year,
        discoveryTags = discoveryTags,
        format = "FLAC",
        scanTimestampMillis = 1
    )
}
