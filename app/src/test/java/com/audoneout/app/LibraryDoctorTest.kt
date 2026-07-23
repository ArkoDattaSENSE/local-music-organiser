package com.audoneout.app

import com.audoneout.app.data.TrackEntity
import com.audoneout.app.doctor.LibraryDoctor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDoctorTest {
    private val doctor = LibraryDoctor()

    @Test
    fun detectsDuplicateCandidates() {
        val tracks = listOf(track(1), track(2))

        assertEquals(1, doctor.duplicateGroups(tracks).size)
        assertTrue(doctor.analyse(tracks).any { it.type == "Possible duplicate" })
    }

    @Test
    fun detectsSpellingVariantsAndLowBitrateLossyFiles() {
        val tracks = listOf(
            track(1, artist = "The Example", format = "MP3", bitrate = 64_000),
            track(2, artist = "the example", format = "FLAC")
        )

        val issues = doctor.analyse(tracks)

        assertTrue(issues.any { it.type == "Inconsistent artist spelling" })
        assertTrue(issues.any { it.type == "Low-bitrate file" && it.trackId == 1L })
    }

    private fun track(
        id: Long,
        artist: String = "Artist",
        format: String = "FLAC",
        bitrate: Int? = null
    ) = TrackEntity(
        id = id,
        mediaStoreId = id,
        contentUri = "content://track/$id",
        title = "Song",
        artist = artist,
        album = "Album",
        durationMs = 180_000,
        folder = "Music",
        fileName = "song$id.flac",
        mimeType = if (format == "FLAC") "audio/flac" else "audio/mpeg",
        sizeBytes = 1000,
        dateAddedSeconds = 1,
        dateModifiedSeconds = 1,
        relativePath = "Music/",
        format = format,
        bitrate = bitrate,
        scanTimestampMillis = 1
    )
}
