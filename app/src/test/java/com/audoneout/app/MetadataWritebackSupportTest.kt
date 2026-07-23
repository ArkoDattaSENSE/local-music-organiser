package com.audoneout.app

import com.audoneout.app.metadata.MetadataWritebackSupport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataWritebackSupportTest {
    @Test
    fun commonTaggedAudioFormatsAreSupported() {
        assertTrue(MetadataWritebackSupport.supports("track.FLAC"))
        assertTrue(MetadataWritebackSupport.supports("track.mp3"))
        assertTrue(MetadataWritebackSupport.supports("track.m4a"))
        assertTrue(MetadataWritebackSupport.supports("track.opus"))
    }

    @Test
    fun playlistsAndUnknownFilesStayCatalogOnly() {
        assertFalse(MetadataWritebackSupport.supports("mix.m3u8"))
        assertFalse(MetadataWritebackSupport.supports("audio.raw"))
        assertFalse(MetadataWritebackSupport.supports("no-extension"))
    }
}
