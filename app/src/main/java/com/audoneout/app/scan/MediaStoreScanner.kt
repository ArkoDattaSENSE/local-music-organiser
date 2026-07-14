package com.audoneout.app.scan

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.audoneout.app.data.FolderBlacklistRuleEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.domain.ScanProgress
import com.audoneout.app.domain.Track
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class ScanResult(
    val tracks: List<TrackEntity>,
    val progress: ScanProgress
)

class MediaStoreScanner @Inject constructor(
    private val contentResolver: ContentResolver
) {
    fun scanAudio(
        blacklistRules: List<FolderBlacklistRuleEntity>,
        rootFilter: String? = null
    ): Flow<ScanResult> = flow {
        val now = System.currentTimeMillis()
        val audioUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val tracks = mutableListOf<TrackEntity>()
        var found = 0
        var excluded = 0
        contentResolver.query(
            audioUri,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val trackNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

            while (cursor.moveToNext()) {
                val relativePath = cursor.getStringOrEmpty(relativePathColumn)
                if (rootFilter != null && !relativePath.startsWith(rootFilter, ignoreCase = true)) {
                    continue
                }
                found += 1
                if (BlacklistMatcher.isExcluded(relativePath, blacklistRules)) {
                    excluded += 1
                    continue
                }
                val mediaId = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(audioUri, mediaId).toString()
                val albumId = cursor.getLongOrNull(albumIdColumn)
                val albumArtUri = albumId?.let {
                    ContentUris.withAppendedId(
                        UriConstants.ALBUM_ART_BASE_URI,
                        it
                    ).toString()
                }
                val fileName = cursor.getStringOrEmpty(displayNameColumn)
                val mimeType = cursor.getStringOrEmpty(mimeColumn)
                val track = Track(
                    mediaStoreId = mediaId,
                    contentUri = contentUri,
                    title = cursor.getStringOrEmpty(titleColumn).ifBlank { fileName.substringBeforeLast('.') },
                    artist = cursor.getStringOrEmpty(artistColumn),
                    album = cursor.getStringOrEmpty(albumColumn),
                    durationMs = cursor.getLongOrZero(durationColumn),
                    folder = relativePath.trimEnd('/').substringAfterLast('/'),
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = cursor.getLongOrZero(sizeColumn),
                    dateAddedSeconds = cursor.getLongOrZero(dateAddedColumn),
                    albumArtUri = albumArtUri,
                    relativePath = relativePath,
                    dateModifiedSeconds = cursor.getLongOrZero(dateModifiedColumn),
                    trackNumber = cursor.getIntOrNull(trackNumberColumn),
                    year = cursor.getIntOrNull(yearColumn)
                )
                tracks += track.toEntity(now)
                if (found % 50 == 0) {
                    emit(
                        ScanResult(
                            tracks = tracks.toList(),
                            progress = ScanProgress(
                                currentRoot = rootFilter ?: "MediaStore audio",
                                currentFolder = relativePath,
                                tracksFound = found,
                                excludedTracks = excluded,
                                estimatedRemainingWork = "Scanning"
                            )
                        )
                    )
                }
            }
        }
        emit(
            ScanResult(
                tracks = tracks,
                progress = ScanProgress(
                    currentRoot = rootFilter ?: "MediaStore audio",
                    tracksFound = found,
                    newTracks = tracks.size,
                    excludedTracks = excluded,
                    estimatedRemainingWork = "Complete"
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun Track.toEntity(scanTime: Long) = TrackEntity(
        mediaStoreId = mediaStoreId,
        contentUri = contentUri,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        durationMs = durationMs,
        folder = folder,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateAddedSeconds = dateAddedSeconds,
        dateModifiedSeconds = dateModifiedSeconds,
        relativePath = relativePath,
        rootId = rootId,
        albumArtUri = albumArtUri,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        format = format,
        bitrate = bitrate,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        scanTimestampMillis = scanTime,
        enhancementStatus = if (artist.isBlank() || album.isBlank() || title.isBlank()) "MissingMetadata" else "Ready"
    )
}

private object UriConstants {
    val ALBUM_ART_BASE_URI = android.net.Uri.parse("content://media/external/audio/albumart")
}

private fun android.database.Cursor.getStringOrEmpty(index: Int): String = getString(index).orEmpty()
private fun android.database.Cursor.getLongOrZero(index: Int): Long = if (isNull(index)) 0L else getLong(index)
private fun android.database.Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
private fun android.database.Cursor.getIntOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index).takeIf { it > 0 }

