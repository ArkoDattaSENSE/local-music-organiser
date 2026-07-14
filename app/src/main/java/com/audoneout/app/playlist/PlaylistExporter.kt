package com.audoneout.app.playlist

import com.audoneout.app.data.TrackEntity
import javax.inject.Inject

class PlaylistExporter @Inject constructor() {
    fun toM3u8(name: String, tracks: List<TrackEntity>): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#PLAYLIST:$name")
        tracks.forEach { track ->
            appendLine("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}")
            appendLine(track.contentUri)
        }
    }

    fun toMigrationCsv(tracks: List<TrackEntity>): String = buildString {
        appendLine("Position,Title,Artist,Album,Album Artist,Year,Duration,ISRC,MusicBrainz Recording ID,YouTube Music Candidate,Spotify Candidate,Match Confidence,Notes")
        tracks.forEachIndexed { index, track ->
            appendLine(
                listOf(
                    (index + 1).toString(),
                    track.title,
                    track.artist,
                    track.album,
                    track.albumArtist,
                    track.year?.toString().orEmpty(),
                    (track.durationMs / 1000).toString(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "Exported by AudOneOut"
                ).joinToString(",") { it.csvEscape() }
            )
        }
    }

    fun soundiizMigrationGuide(csvFileName: String = "audoneout-playlist.csv"): String =
        """
        Soundiiz handoff:
        1. Export $csvFileName from AudOneOut.
        2. Open Soundiiz and choose Import playlist from file.
        3. Upload the CSV and map Title, Artist, Album, Album Artist, Year, and Duration.
        4. Review unmatched or low-confidence rows manually before saving to a streaming service.
        AudOneOut does not modify streaming-service accounts directly.
        """.trimIndent()

    private fun String.csvEscape(): String {
        val escaped = replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '\n' || it == '"' }) "\"$escaped\"" else escaped
    }
}
