package com.audoneout.app.playlist

import javax.inject.Inject

data class ImportedPlaylist(
    val name: String,
    val entries: List<ImportedPlaylistEntry>,
    val warnings: List<String> = emptyList()
)

data class ImportedPlaylistEntry(
    val position: Int,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationSeconds: Long? = null,
    val reference: String = "",
    val notes: String = ""
)

class PlaylistImporter @Inject constructor() {
    fun fromM3u(name: String, text: String): ImportedPlaylist {
        val entries = mutableListOf<ImportedPlaylistEntry>()
        val warnings = mutableListOf<String>()
        var pendingInfo: ExtInfo? = null
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                when {
                    line.startsWith("#EXTINF:", ignoreCase = true) -> pendingInfo = parseExtInfo(line)
                    line.startsWith("#") -> Unit
                    else -> {
                        val info = pendingInfo
                        entries += ImportedPlaylistEntry(
                            position = entries.size + 1,
                            title = info?.title.orEmpty(),
                            artist = info?.artist.orEmpty(),
                            durationSeconds = info?.durationSeconds,
                            reference = line,
                            notes = if (line.startsWith("content://") || line.startsWith("/") || line.startsWith("file://")) {
                                ""
                            } else {
                                "Reference may need manual matching"
                            }
                        )
                        pendingInfo = null
                    }
                }
            }
        if (entries.isEmpty()) warnings += "No playlist entries were found."
        return ImportedPlaylist(name = name, entries = entries, warnings = warnings)
    }

    fun fromCsv(name: String, text: String): ImportedPlaylist {
        val rows = parseCsv(text)
        if (rows.isEmpty()) {
            return ImportedPlaylist(name, emptyList(), listOf("CSV is empty."))
        }
        val header = rows.first().map { it.trim().lowercase() }
        val dataRows = rows.drop(1)
        fun indexOf(vararg candidates: String): Int =
            candidates.firstNotNullOfOrNull { candidate -> header.indexOf(candidate).takeIf { it >= 0 } } ?: -1
        val titleIndex = indexOf("title", "track")
        val artistIndex = indexOf("artist", "artists")
        val albumIndex = indexOf("album")
        val durationIndex = indexOf("duration", "durationseconds", "duration seconds")
        val notesIndex = indexOf("notes")
        val warnings = mutableListOf<String>()
        if (titleIndex < 0 && artistIndex < 0) {
            warnings += "CSV did not include recognizable Title or Artist columns."
        }
        val entries = dataRows.mapIndexed { index, row ->
            ImportedPlaylistEntry(
                position = index + 1,
                title = row.getOrBlank(titleIndex),
                artist = row.getOrBlank(artistIndex),
                album = row.getOrBlank(albumIndex),
                durationSeconds = row.getOrBlank(durationIndex).toLongOrNull(),
                reference = "",
                notes = row.getOrBlank(notesIndex)
            )
        }
        return ImportedPlaylist(name = name, entries = entries, warnings = warnings)
    }

    private fun parseExtInfo(line: String): ExtInfo {
        val payload = line.substringAfter(':', "").trim()
        val duration = payload.substringBefore(',', "").toLongOrNull()
        val label = payload.substringAfter(',', "").trim()
        val artist = label.substringBefore(" - ", "").takeIf { it != label }.orEmpty()
        val title = if (artist.isBlank()) label else label.substringAfter(" - ").trim()
        return ExtInfo(durationSeconds = duration, artist = artist, title = title)
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && inQuotes && text.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    currentRow += current.toString()
                    current.clear()
                }
                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index += 1
                    currentRow += current.toString()
                    current.clear()
                    if (currentRow.any { it.isNotBlank() }) rows += currentRow.toList()
                    currentRow.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        currentRow += current.toString()
        if (currentRow.any { it.isNotBlank() }) rows += currentRow.toList()
        return rows
    }

    private fun List<String>.getOrBlank(index: Int): String =
        if (index in indices) get(index).trim() else ""
}

private data class ExtInfo(
    val durationSeconds: Long?,
    val artist: String,
    val title: String
)
