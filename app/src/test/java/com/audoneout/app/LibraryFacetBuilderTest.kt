package com.audoneout.app

import com.audoneout.app.data.LibraryFacetBuilder
import com.audoneout.app.data.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryFacetBuilderTest {
    @Test
    fun buildsAlbumArtistAndFolderSummariesFromAvailableTracks() {
        val facets = LibraryFacetBuilder.build(
            tracks = listOf(
                track(title = "First", artist = "The Artist", album = "One Album", durationMs = 60_000, sizeBytes = 1_000),
                track(id = 2, mediaStoreId = 2, title = "Second", artist = "The Artist", album = "One Album", durationMs = 90_000, sizeBytes = 2_000),
                track(id = 3, mediaStoreId = 3, title = "Hidden", artist = "Other", album = "Other Album", availability = "Missing")
            ),
            nowMillis = 42L
        )

        assertEquals(1, facets.albums.size)
        assertEquals("One Album", facets.albums.single().title)
        assertEquals(2, facets.albums.single().trackCount)
        assertEquals(150_000, facets.albums.single().durationMs)

        assertEquals(1, facets.artists.size)
        assertEquals("The Artist", facets.artists.single().name)
        assertEquals(2, facets.artists.single().trackCount)

        assertEquals(1, facets.folders.size)
        assertEquals("Music", facets.folders.single().name)
        assertEquals(3_000, facets.folders.single().storageBytes)
        assertEquals(42L, facets.folders.single().lastSeenMillis)
    }

    @Test
    fun normalisesAlbumAndArtistKeysForStableGrouping() {
        val facets = LibraryFacetBuilder.build(
            tracks = listOf(
                track(title = "A", artist = "Bengali Rock!", album = "Rainy Nights"),
                track(id = 2, mediaStoreId = 2, title = "B", artist = "bengali rock", album = "Rainy  Nights")
            )
        )

        assertEquals(1, facets.albums.size)
        assertEquals(1, facets.artists.size)
        assertFalse(facets.albums.single().albumKey.contains("!"))
    }

    private fun track(
        id: Long = 1,
        mediaStoreId: Long = 1,
        title: String,
        artist: String,
        album: String,
        durationMs: Long = 120_000,
        sizeBytes: Long = 4_000,
        availability: String = "Available"
    ) = TrackEntity(
        id = id,
        mediaStoreId = mediaStoreId,
        contentUri = "content://audio/$mediaStoreId",
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        folder = "Music",
        fileName = "$title.flac",
        mimeType = "audio/flac",
        sizeBytes = sizeBytes,
        dateAddedSeconds = mediaStoreId,
        dateModifiedSeconds = mediaStoreId,
        relativePath = "Music/",
        scanTimestampMillis = 1L,
        format = "FLAC",
        availability = availability
    )
}
