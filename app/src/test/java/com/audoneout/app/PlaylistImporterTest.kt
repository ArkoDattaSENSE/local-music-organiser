package com.audoneout.app

import com.audoneout.app.playlist.PlaylistImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImporterTest {
    private val importer = PlaylistImporter()

    @Test
    fun importsM3uEntriesWithExtInfoAndReferences() {
        val playlist = importer.fromM3u(
            name = "Walk",
            text = """
                #EXTM3U
                #EXTINF:181,Artist - Rain Song
                content://media/external/audio/media/1
                #EXTINF:200,Loose Track
                relative/path/song.flac
            """.trimIndent()
        )

        assertEquals(2, playlist.entries.size)
        assertEquals("Artist", playlist.entries.first().artist)
        assertEquals("Rain Song", playlist.entries.first().title)
        assertEquals(181L, playlist.entries.first().durationSeconds)
        assertTrue(playlist.entries.last().notes.contains("manual matching"))
    }

    @Test
    fun importsCsvRowsWithQuotedCommas() {
        val playlist = importer.fromCsv(
            name = "Migration",
            text = """
                Position,Title,Artist,Album,Duration,Notes
                1,"Rain, Again","Artist, With Comma",Album,120,"Needs review"
            """.trimIndent()
        )

        assertEquals(1, playlist.entries.size)
        assertEquals("Rain, Again", playlist.entries.single().title)
        assertEquals("Artist, With Comma", playlist.entries.single().artist)
        assertEquals(120L, playlist.entries.single().durationSeconds)
        assertEquals("Needs review", playlist.entries.single().notes)
    }
}
