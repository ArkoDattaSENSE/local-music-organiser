package com.audoneout.app

import com.audoneout.app.data.TrackEntity
import com.audoneout.app.playlist.PlaylistExporter
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistExporterTest {
    @Test
    fun exportsM3uAndCsv() {
        val track = TrackEntity(
            mediaStoreId = 1,
            contentUri = "content://track/1",
            title = "Rain",
            artist = "Artist, With Comma",
            album = "Album",
            durationMs = 120_000,
            folder = "Music",
            fileName = "rain.flac",
            mimeType = "audio/flac",
            sizeBytes = 1000,
            dateAddedSeconds = 1,
            dateModifiedSeconds = 1,
            relativePath = "Music/",
            format = "FLAC",
            scanTimestampMillis = 1
        )
        val exporter = PlaylistExporter()

        assertTrue(exporter.toM3u8("Mix", listOf(track)).contains("#EXTM3U"))
        assertTrue(exporter.toMigrationCsv(listOf(track)).contains("\"Artist, With Comma\""))
    }
}

