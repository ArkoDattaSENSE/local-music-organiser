package com.audoneout.app

import com.audoneout.app.playlist.LibrarySummary
import com.audoneout.app.playlist.LocalRuleBasedPromptInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPromptInterpreterTest {
    private val interpreter = LocalRuleBasedPromptInterpreter()

    @Test
    fun parsesDurationFormatAndArtistRepetition() {
        val rules = interpreter.parse(
            "Make a 45-minute playlist without repeating artists, only use FLAC files",
            LibrarySummary(formats = listOf("FLAC"))
        )

        assertEquals(45, rules.targetDurationMinutes)
        assertTrue(rules.fileFormats.contains("FLAC"))
        assertTrue(rules.avoidArtistRepetitions)
    }
}

