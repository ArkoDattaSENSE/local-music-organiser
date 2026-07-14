package com.audoneout.app

import com.audoneout.app.data.FolderBlacklistRuleEntity
import com.audoneout.app.scan.BlacklistMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlacklistMatcherTest {
    @Test
    fun matchesFolderNameCaseInsensitively() {
        val rules = listOf(
            FolderBlacklistRuleEntity(label = "WhatsApp Audio", pattern = "WhatsApp Audio", matchType = BlacklistMatcher.FOLDER_NAME)
        )

        assertTrue(BlacklistMatcher.isExcluded("Music/whatsapp audio/song.mp3", rules))
        assertFalse(BlacklistMatcher.isExcluded("Music/Albums/song.mp3", rules))
    }

    @Test
    fun defaultSuggestionsCoverBriefExclusions() {
        val defaultPatterns = BlacklistMatcher.defaultRules.map { it.pattern.lowercase() }

        assertTrue("podcasts" in defaultPatterns)
        assertTrue("audiobooks" in defaultPatterns)
        assertTrue(".thumbnails" in defaultPatterns)
        assertTrue("cache" in defaultPatterns)
    }

    @Test
    fun matchesPathPatternsWithWildcards() {
        val rules = listOf(
            FolderBlacklistRuleEntity(label = "App cache", pattern = "Android/*/cache", matchType = BlacklistMatcher.PATH_PATTERN)
        )

        assertTrue(BlacklistMatcher.isExcluded("Android/data/cache/song.mp3", rules))
        assertFalse(BlacklistMatcher.isExcluded("Music/data/cache/song.mp3", rules))
    }
}
