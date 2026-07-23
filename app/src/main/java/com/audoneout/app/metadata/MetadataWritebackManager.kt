package com.audoneout.app.metadata

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.audoneout.app.data.LibraryDao
import com.audoneout.app.data.MetadataWritebackEntity
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.data.UserConfirmedMetadataEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MetadataWritebackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    private val dao: LibraryDao
) {
    suspend fun permissionRequest(trackId: Long): IntentSender? = withContext(Dispatchers.IO) {
        val track = dao.findTrack(trackId) ?: error("Track is no longer in the library")
        requireSupported(track)
        val uri = Uri.parse(track.contentUri)
        try {
            contentResolver.openFileDescriptor(uri, "rw")?.use { }
                ?: error("Android could not open this audio file")
            null
        } catch (error: RecoverableSecurityException) {
            error.userAction.actionIntent.intentSender
        } catch (error: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createWriteRequest(contentResolver, listOf(uri)).intentSender
            } else {
                throw error
            }
        }
    }

    suspend fun write(trackId: Long): MetadataWritebackResult = withContext(Dispatchers.IO) {
        val track = dao.findTrack(trackId) ?: error("Track is no longer in the library")
        val confirmed = dao.findUserConfirmedMetadata(trackId)
            ?: error("Review or edit metadata before writing tags")
        requireSupported(track)
        val fields = confirmed.toTagFields()
        require(fields.isNotEmpty()) { "There are no user-confirmed tags to write" }
        val requestedAt = System.currentTimeMillis()
        dao.upsertMetadataWriteback(
            MetadataWritebackEntity(
                trackId = trackId,
                status = "Writing",
                requestedAtMillis = requestedAt
            )
        )

        runCatching {
            val uri = Uri.parse(track.contentUri)
            val properties = contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                TagLibBridge.readProperties(descriptor.dup().detachFd())
                    ?: error("This audio format does not expose writable tags")
            } ?: error("Android could not open this audio file")

            fields.forEach { field ->
                if (field.value.isBlank()) {
                    properties.remove(field.key)
                } else {
                    properties[field.key] = arrayOf(field.value)
                }
            }

            val saved = contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                TagLibBridge.saveProperties(descriptor.dup().detachFd(), properties)
            } ?: false
            check(saved) { "The tag library could not save this file" }

            val verified = contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                TagLibBridge.readProperties(descriptor.dup().detachFd())
            } ?: error("The updated tags could not be verified")
            val written = fields.filter { it.matches(verified[it.key]) }
            val catalogOnly = fields - written.toSet()
            check(written.isNotEmpty()) { "The file format rejected all requested tag changes" }

            updateMediaStore(uri, confirmed)
            val status = if (catalogOnly.isEmpty()) "Written" else "Partial"
            val completedAt = System.currentTimeMillis()
            dao.upsertMetadataWriteback(
                MetadataWritebackEntity(
                    trackId = trackId,
                    status = status,
                    requestedAtMillis = requestedAt,
                    completedAtMillis = completedAt,
                    writtenFields = written.joinToString { it.label },
                    catalogOnlyFields = catalogOnly.joinToString { it.label },
                    errorMessage = if (catalogOnly.isEmpty()) "" else {
                        "This format kept ${catalogOnly.joinToString { it.label }} in AudOneOut only"
                    }
                )
            )
            contentResolver.notifyChange(uri, null)
            MetadataWritebackResult(
                trackId = trackId,
                status = status,
                writtenFields = written.map { it.label },
                catalogOnlyFields = catalogOnly.map { it.label }
            )
        }.getOrElse { error ->
            dao.upsertMetadataWriteback(
                MetadataWritebackEntity(
                    trackId = trackId,
                    status = "Failed",
                    requestedAtMillis = requestedAt,
                    completedAtMillis = System.currentTimeMillis(),
                    errorMessage = error.message ?: "Metadata writeback failed"
                )
            )
            throw error
        }
    }

    private fun requireSupported(track: TrackEntity) {
        require(MetadataWritebackSupport.supports(track.fileName)) {
            "${track.fileName.substringAfterLast('.', "This file format").uppercase()} tag writeback is not supported"
        }
    }

    private fun updateMediaStore(uri: Uri, metadata: UserConfirmedMetadataEntity) {
        val values = ContentValues().apply {
            putConfirmed(MediaStore.Audio.Media.TITLE, metadata.title)
            putConfirmed(MediaStore.Audio.Media.ARTIST, metadata.artist)
            putConfirmed(MediaStore.Audio.Media.ALBUM, metadata.album)
            putConfirmed(MediaStore.Audio.Media.GENRE, metadata.genre)
            metadata.year?.let { put(MediaStore.Audio.Media.YEAR, it) }
            metadata.trackNumber?.let { put(MediaStore.Audio.Media.TRACK, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                putConfirmed(MediaStore.MediaColumns.ALBUM_ARTIST, metadata.albumArtist)
            }
        }
        if (values.size() > 0) {
            runCatching { contentResolver.update(uri, values, null, null) }
        }
    }

    private fun ContentValues.putConfirmed(key: String, value: String?) {
        when {
            value == null -> Unit
            value.isBlank() -> putNull(key)
            else -> put(key, value)
        }
    }

    private data class TagField(val label: String, val key: String, val value: String) {
        fun matches(actual: Array<String>?): Boolean =
            if (value.isBlank()) actual.isNullOrEmpty() else actual?.any { it.trim() == value.trim() } == true
    }

    private fun UserConfirmedMetadataEntity.toTagFields(): List<TagField> = buildList {
        title?.let { add(TagField("title", "TITLE", it)) }
        artist?.let { add(TagField("artist", "ARTIST", it)) }
        album?.let { add(TagField("album", "ALBUM", it)) }
        albumArtist?.let { add(TagField("album artist", "ALBUMARTIST", it)) }
        genre?.let { add(TagField("genre", "GENRE", it)) }
        year?.let { add(TagField("year", "DATE", it.toString())) }
        trackNumber?.let { add(TagField("track number", "TRACKNUMBER", it.toString())) }
        discNumber?.let { add(TagField("disc number", "DISCNUMBER", it.toString())) }
        language?.let { add(TagField("language", "LANGUAGE", it)) }
        mood?.let { add(TagField("mood", "MOOD", it)) }
        energy?.let { add(TagField("energy", "AUDONEOUT_ENERGY", it.toString())) }
        discoveryTags?.let { add(TagField("discovery tags", "AUDONEOUT_TAGS", it)) }
    }
}
