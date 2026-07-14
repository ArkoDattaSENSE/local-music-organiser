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
}

