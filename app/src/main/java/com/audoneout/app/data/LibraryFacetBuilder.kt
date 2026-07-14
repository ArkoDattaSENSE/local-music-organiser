package com.audoneout.app.data

data class LibraryFacets(
    val albums: List<AlbumEntity>,
    val artists: List<ArtistEntity>,
    val folders: List<FolderEntity>
)

object LibraryFacetBuilder {
    fun build(tracks: List<TrackEntity>, nowMillis: Long = System.currentTimeMillis()): LibraryFacets {
        val availableTracks = tracks.filter { it.availability == "Available" }
        val albums = availableTracks
            .filter { it.album.isNotBlank() }
            .groupBy { "${it.album.normalizeFacetKey()}|${it.albumArtist.ifBlank { it.artist }.normalizeFacetKey()}" }
            .map { (key, group) ->
                val first = group.maxByOrNull { it.dateAddedSeconds } ?: group.first()
                AlbumEntity(
                    albumKey = key,
                    title = first.album.ifBlank { "Unknown Album" },
                    artistName = first.albumArtist.ifBlank { first.artist }.ifBlank { "Unknown Artist" },
                    trackCount = group.size,
                    durationMs = group.sumOf { it.durationMs },
                    storageBytes = group.sumOf { it.sizeBytes },
                    artworkUri = group.firstNotNullOfOrNull { it.albumArtUri },
                    lastAddedSeconds = group.maxOf { it.dateAddedSeconds }
                )
            }
        val artists = availableTracks
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artist.normalizeFacetKey() }
            .map { (key, group) ->
                ArtistEntity(
                    artistKey = key,
                    name = group.first().artist.ifBlank { "Unknown Artist" },
                    trackCount = group.size,
                    albumCount = group.map { it.album.normalizeFacetKey() }.filter { it.isNotBlank() }.distinct().size,
                    durationMs = group.sumOf { it.durationMs }
                )
            }
        val folders = availableTracks
            .filter { it.relativePath.isNotBlank() }
            .groupBy { it.relativePath.trimEnd('/') }
            .map { (path, group) ->
                FolderEntity(
                    path = path,
                    name = path.substringAfterLast('/').ifBlank { path },
                    trackCount = group.size,
                    storageBytes = group.sumOf { it.sizeBytes },
                    included = true,
                    lastSeenMillis = nowMillis
                )
            }
        return LibraryFacets(albums = albums, artists = artists, folders = folders)
    }
}

private fun String.normalizeFacetKey(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
