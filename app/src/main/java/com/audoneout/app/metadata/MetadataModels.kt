package com.audoneout.app.metadata

import com.audoneout.app.data.TrackEntity

data class TrackMetadataInput(
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val language: String,
    val mood: String,
    val energy: Int?,
    val discoveryTags: String
) {
    companion object {
        fun from(track: TrackEntity) = TrackMetadataInput(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumArtist = track.albumArtist,
            genre = track.genre,
            year = track.year,
            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            language = track.language,
            mood = track.mood,
            energy = track.energy,
            discoveryTags = track.discoveryTags
        )
    }
}

data class MetadataWritebackResult(
    val trackId: Long,
    val status: String,
    val writtenFields: List<String>,
    val catalogOnlyFields: List<String>
)

object MetadataWritebackSupport {
    private val supportedExtensions = setOf(
        "mp3", "flac", "ogg", "oga", "opus", "m4a", "mp4", "wav", "wave",
        "aif", "aiff", "wma", "asf", "dsf", "ape", "mpc", "wv", "spx", "tta",
        "mod", "s3m", "it", "xm"
    )

    fun supports(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in supportedExtensions
}
