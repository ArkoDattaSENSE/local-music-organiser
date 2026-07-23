package com.audoneout.app

import com.audoneout.app.radio.RadioStreamResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioStreamResolverTest {
    private val resolver = RadioStreamResolver()

    @Test
    fun parsesM3uAndIgnoresComments() {
        val streams = resolver.parsePlaylist(
            """
            #EXTM3U
            #EXTINF:-1,Station
            https://radio.example/live.mp3
            """.trimIndent()
        )

        assertEquals(listOf("https://radio.example/live.mp3"), streams)
    }

    @Test
    fun parsesPlsFileEntriesInOrder() {
        val streams = resolver.parsePlaylist(
            """
            [playlist]
            File1=http://one.example/stream
            Title1=One
            File2=https://two.example/stream
            """.trimIndent()
        )

        assertEquals(listOf("http://one.example/stream", "https://two.example/stream"), streams)
    }
}
