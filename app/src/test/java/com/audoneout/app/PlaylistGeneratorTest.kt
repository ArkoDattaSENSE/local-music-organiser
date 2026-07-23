package com.audoneout.app

import com.audoneout.app.data.TrackEntity
import com.audoneout.app.domain.PlaylistRules
import com.audoneout.app.playlist.LocalRuleBasedPromptInterpreter
import com.audoneout.app.playlist.PlaylistGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistGeneratorTest {
    private val generator = PlaylistGenerator()

    @Test
    fun losslessAndRecentRulesAreHardFilters() {
        val now = 2_000_000_000L
        val rules = LocalRuleBasedPromptInterpreter().parse("Only use lossless songs added this month")
        val result = generator.generate(
            tracks = listOf(
                track(1, "Recent FLAC", "A", "FLAC", now - 2 * 86_400L),
                track(2, "Recent MP3", "B", "MP3", now - 2 * 86_400L),
                track(3, "Old FLAC", "C", "FLAC", now - 60 * 86_400L)
            ),
            rules = rules,
            nowSeconds = now
        )

        assertEquals(listOf(1L), result.map { it.track.libraryId })
    }

    @Test
    fun languageAndFolderRulesExcludeUnknownMatches() {
        val rules = PlaylistRules(
            languages = listOf("Bengali"),
            folders = listOf("Music/Bangla")
        )
        val result = generator.generate(
            tracks = listOf(
                track(1, "Match", "A", language = "Bengali", path = "Music/Bangla/"),
                track(2, "Wrong language", "B", language = "English", path = "Music/Bangla/"),
                track(3, "Wrong folder", "C", language = "Bengali", path = "Music/English/")
            ),
            rules = rules
        )

        assertEquals(listOf(1L), result.map { it.track.libraryId })
    }

    @Test
    fun targetDurationAvoidsArtistRepetition() {
        val rules = PlaylistRules(targetDurationMinutes = 6, avoidArtistRepetitions = true)
        val result = generator.generate(
            tracks = listOf(
                track(1, "One", "Artist A"),
                track(2, "Two", "Artist A"),
                track(3, "Three", "Artist B"),
                track(4, "Four", "Artist C")
            ),
            rules = rules
        )

        assertEquals(2, result.size)
        assertEquals(2, result.map { it.track.artist }.distinct().size)
        assertTrue(result.sumOf { it.track.durationMs } >= 6 * 60_000L)
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        format: String = "FLAC",
        dateAddedSeconds: Long = 2_000_000_000L,
        language: String = "",
        path: String = "Music/"
    ) = TrackEntity(
        id = id,
        mediaStoreId = id,
        contentUri = "content://media/external/audio/media/$id",
        title = title,
        artist = artist,
        album = "Album",
        durationMs = 3 * 60_000L,
        folder = path.trimEnd('/').substringAfterLast('/'),
        fileName = "$title.${format.lowercase()}",
        mimeType = "audio/${format.lowercase()}",
        sizeBytes = 10_000,
        dateAddedSeconds = dateAddedSeconds,
        dateModifiedSeconds = dateAddedSeconds,
        relativePath = path,
        format = format,
        language = language,
        scanTimestampMillis = dateAddedSeconds * 1000
    )
}
