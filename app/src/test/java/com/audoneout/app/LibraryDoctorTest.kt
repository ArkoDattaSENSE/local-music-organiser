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

    private fun track(id: Long) = TrackEntity(
        id = id,
        mediaStoreId = id,
        contentUri = "content://track/$id",
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        folder = "Music",
        fileName = "song$id.flac",
        mimeType = "audio/flac",
        sizeBytes = 1000,
        dateAddedSeconds = 1,
        dateModifiedSeconds = 1,
        relativePath = "Music/",
        format = "FLAC",
        scanTimestampMillis = 1
    )
}

