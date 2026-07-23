package com.audoneout.app

import com.audoneout.app.data.TrackEntity
import com.audoneout.app.metadata.LocalMetadataEnricher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMetadataEnricherTest {
    @Test
    fun derivesTransparentDiscoverySignalsFromExistingMetadata() {
        val values = LocalMetadataEnricher.infer(track(genre = "Ambient Electronic", year = 1997))
            .associateBy { it.key }

        assertEquals("1990s", values["era"]?.value)
        assertEquals("lossless", values["quality"]?.value)
        assertEquals("relaxed", values["mood"]?.value)
        assertEquals("electronic", values["character"]?.value)
        assertTrue(values["discoveryTags"]?.value.orEmpty().contains("1990s"))
        assertTrue(values.all { it.value.confidence in 0f..1f })
    }

    @Test
    fun unknownGenreDoesNotInventMoodOrEnergy() {
        val values = LocalMetadataEnricher.infer(track(genre = "", year = null))

        assertTrue(values.none { it.key == "mood" || it.key == "energy" })
        assertTrue(values.any { it.key == "quality" && it.value == "lossless" })
    }

    private fun track(genre: String, year: Int?) = TrackEntity(
        id = 1,
        mediaStoreId = 1,
        contentUri = "content://track/1",
        title = "Track",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        folder = "Music",
        fileName = "track.flac",
        mimeType = "audio/flac",
        sizeBytes = 20_000_000,
        dateAddedSeconds = 1,
        dateModifiedSeconds = 1,
        relativePath = "Music/",
        genre = genre,
        year = year,
        format = "FLAC",
        scanTimestampMillis = 1
    )
}
