package com.audoneout.app

import com.audoneout.app.lastfm.LastFmLinks
import com.audoneout.app.lastfm.trackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmLinksTest {
    @Test
    fun youtubeMusicSearchEncodesArtistAndTitle() {
        val url = LastFmLinks.youtubeMusicSearch("AC/DC", "It's a Long Way")

        assertTrue(url.startsWith("https://music.youtube.com/search?q="))
        assertTrue(url.contains("AC%2FDC%20It%27s%20a%20Long%20Way"))
    }

    @Test
    fun profileUrlEncodesUsername() {
        assertEquals("https://www.last.fm/user/space%20user", LastFmLinks.profile("space user"))
    }

    @Test
    fun trackMatchingNormalizesPunctuationAndFeaturingCredits() {
        assertEquals(
            trackKey("Earth, Wind & Fire", "September"),
            trackKey("Earth Wind and Fire", "September (feat. Guest)")
        )
    }

    @Test
    fun trackMatchingPreservesNonLatinTitles() {
        val first = trackKey(
            "Anupam Roy",
            "\u0986\u09ae\u09be\u0995\u09c7 \u0986\u09ae\u09be\u09b0 \u09ae\u09a4\u09cb " +
                "\u09a5\u09be\u0995\u09a4\u09c7 \u09a6\u09be\u0993"
        )
        val second = trackKey(
            "Anupam Roy",
            "\u09ac\u09c7\u0981\u099a\u09c7 \u09a5\u09be\u0995\u09be\u09b0 \u0997\u09be\u09a8"
        )

        assertTrue(first != second)
        assertTrue(first.contains("\u0986\u09ae\u09be\u0995\u09c7"))
    }
}
