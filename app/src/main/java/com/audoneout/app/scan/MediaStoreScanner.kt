package com.audoneout.app.scan

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
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
        rootFilter: String? = null,
        rootUri: String? = null
    ): Flow<ScanResult> = flow {
        val now = System.currentTimeMillis()
        val rootVolumeName = rootUri?.toMediaStoreVolumeName()
        val audioUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.MediaColumns.VOLUME_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.RELATIVE_PATH)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.MediaColumns.ALBUM_ARTIST)
                add(MediaStore.MediaColumns.CD_TRACK_NUMBER)
                add(MediaStore.MediaColumns.DISC_NUMBER)
                add(MediaStore.MediaColumns.BITRATE)
                add(MediaStore.Audio.Media.GENRE)
            }
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val batch = mutableListOf<TrackEntity>()
        var found = 0
        var excluded = 0
        val cursor = contentResolver.query(
            audioUri,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        ) ?: error("Android could not read the MediaStore audio catalog")
        cursor.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val volumeNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME)
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
            val albumArtistColumn = cursor.getColumnIndex(MediaStore.MediaColumns.ALBUM_ARTIST)
            val cdTrackNumberColumn = cursor.getColumnIndex(MediaStore.MediaColumns.CD_TRACK_NUMBER)
            val discNumberColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISC_NUMBER)
            val bitrateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.BITRATE)
            val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)

            while (cursor.moveToNext()) {
                val volumeName = cursor.getStringOrEmpty(volumeNameColumn)
                    .ifBlank { MediaStore.VOLUME_EXTERNAL_PRIMARY }
                if (rootVolumeName != null && !volumeName.equals(rootVolumeName, ignoreCase = true)) {
                    continue
                }
                val relativePath = cursor.getStringOrEmpty(relativePathColumn)
                if (rootFilter != null && !relativePath.isInsideRoot(rootFilter)) {
                    continue
                }
                found += 1
                val isExcluded = BlacklistMatcher.isExcluded(relativePath, blacklistRules)
                if (isExcluded) excluded += 1
                val mediaId = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.getContentUri(volumeName),
                    mediaId
                ).toString()
                val albumId = cursor.getLongOrNull(albumIdColumn)
                val albumArtUri = albumId?.let {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/$volumeName/audio/albumart"),
                        it
                    ).toString()
                }
                val fileName = cursor.getStringOrEmpty(displayNameColumn)
                val mimeType = cursor.getStringOrEmpty(mimeColumn)
                val track = Track(
                    mediaStoreId = mediaId,
                    volumeName = volumeName,
                    contentUri = contentUri,
                    title = cursor.getStringOrEmpty(titleColumn).ifBlank { fileName.substringBeforeLast('.') },
                    artist = cursor.getStringOrEmpty(artistColumn),
                    album = cursor.getStringOrEmpty(albumColumn),
                    albumArtist = cursor.getOptionalString(albumArtistColumn),
                    durationMs = cursor.getLongOrZero(durationColumn),
                    folder = relativePath.trimEnd('/').substringAfterLast('/'),
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = cursor.getLongOrZero(sizeColumn),
                    dateAddedSeconds = cursor.getLongOrZero(dateAddedColumn),
                    albumArtUri = albumArtUri,
                    relativePath = relativePath,
                    dateModifiedSeconds = cursor.getLongOrZero(dateModifiedColumn),
                    trackNumber = cursor.getPositiveIntFromString(cdTrackNumberColumn)
                        ?: cursor.getIntOrNull(trackNumberColumn),
                    discNumber = cursor.getPositiveIntFromString(discNumberColumn),
                    year = cursor.getIntOrNull(yearColumn),
                    genre = cursor.getOptionalString(genreColumn),
                    format = fileName.substringAfterLast('.', mimeType.substringAfterLast('/')).uppercase(),
                    bitrate = cursor.getIntOrNull(bitrateColumn)
                )
                batch += track.toEntity(now).let { entity ->
                    if (isExcluded) {
                        entity.copy(availability = "Excluded", enhancementStatus = "Excluded")
                    } else {
                        entity
                    }
                }
                if (batch.size >= 50) {
                    emit(
                        ScanResult(
                            tracks = batch.toList(),
                            progress = ScanProgress(
                                currentRoot = rootFilter ?: "MediaStore audio",
                                currentFolder = relativePath,
                                tracksFound = found,
                                excludedTracks = excluded,
                                estimatedRemainingWork = "Scanning"
                            )
                        )
                    )
                    batch.clear()
                }
            }
        }
        emit(
            ScanResult(
                tracks = batch.toList(),
                progress = ScanProgress(
                    currentRoot = rootFilter ?: "MediaStore audio",
                    tracksFound = found,
                    excludedTracks = excluded,
                    estimatedRemainingWork = "Complete"
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun Track.toEntity(scanTime: Long) = TrackEntity(
        mediaStoreId = mediaStoreId,
        volumeName = volumeName,
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

private fun android.database.Cursor.getStringOrEmpty(index: Int): String = getString(index).orEmpty()
private fun android.database.Cursor.getLongOrZero(index: Int): Long = if (isNull(index)) 0L else getLong(index)
private fun android.database.Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
private fun android.database.Cursor.getIntOrNull(index: Int): Int? =
    if (index < 0 || isNull(index)) null else getInt(index).takeIf { it > 0 }
private fun android.database.Cursor.getOptionalString(index: Int): String =
    if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
private fun android.database.Cursor.getPositiveIntFromString(index: Int): Int? =
    getOptionalString(index).substringBefore('/').trim().toIntOrNull()?.takeIf { it > 0 }

private fun String.isInsideRoot(root: String): Boolean {
    val normalizedPath = trim('/').lowercase()
    val normalizedRoot = root.substringAfterLast(':').trim('/').lowercase()
    return normalizedRoot.isBlank() || normalizedPath == normalizedRoot || normalizedPath.startsWith("$normalizedRoot/")
}

private fun String.toMediaStoreVolumeName(): String? {
    val documentId = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(this))
    }.getOrNull() ?: return null
    val storageId = documentId.substringBefore(':').trim()
    return when {
        storageId.equals("primary", ignoreCase = true) -> MediaStore.VOLUME_EXTERNAL_PRIMARY
        storageId.isBlank() -> null
        else -> storageId.lowercase()
    }
}
