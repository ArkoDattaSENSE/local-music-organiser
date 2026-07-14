package com.audoneout.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "music_roots",
    indices = [Index(value = ["uri"], unique = true), Index(value = ["included"])]
)
data class MusicRootEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val uri: String,
    val location: String,
    val indexedTrackCount: Int = 0,
    val storageBytes: Long = 0,
    val lastScanTimeMillis: Long = 0,
    val scanStatus: String = "Not scanned",
    val included: Boolean = true
)

@Entity(
    tableName = "folder_blacklist_rules",
    indices = [Index(value = ["enabled"]), Index(value = ["matchType"])]
)
data class FolderBlacklistRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val pattern: String,
    val matchType: String,
    val enabled: Boolean = true,
    val defaultSuggestion: Boolean = false,
    val excludedPreviewCount: Int = 0
)

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["contentUri"], unique = true),
        Index(value = ["relativePath"]),
        Index(value = ["rootId"]),
        Index(value = ["dateModifiedSeconds"]),
        Index(value = ["availability"]),
        Index(value = ["enhancementStatus"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["genre"]),
        Index(value = ["language"])
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val durationMs: Long,
    val folder: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val dateModifiedSeconds: Long,
    val relativePath: String,
    val rootId: Long? = null,
    val albumArtUri: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String = "",
    val format: String = "",
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val language: String = "",
    val mood: String = "",
    val energy: Int? = null,
    val discoveryTags: String = "",
    val scanTimestampMillis: Long,
    val availability: String = "Available",
    val enhancementStatus: String = "Ready"
)

@Entity(
    tableName = "albums",
    indices = [Index(value = ["title"]), Index(value = ["artistName"]), Index(value = ["lastAddedSeconds"])]
)
data class AlbumEntity(
    @PrimaryKey val albumKey: String,
    val title: String,
    val artistName: String,
    val trackCount: Int,
    val durationMs: Long,
    val storageBytes: Long,
    val artworkUri: String? = null,
    val lastAddedSeconds: Long
)

@Entity(
    tableName = "artists",
    indices = [Index(value = ["name"]), Index(value = ["albumCount"]), Index(value = ["trackCount"])]
)
data class ArtistEntity(
    @PrimaryKey val artistKey: String,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val durationMs: Long
)

@Entity(
    tableName = "folders",
    indices = [Index(value = ["name"]), Index(value = ["included"]), Index(value = ["trackCount"])]
)
data class FolderEntity(
    @PrimaryKey val path: String,
    val name: String,
    val trackCount: Int,
    val storageBytes: Long,
    val included: Boolean = true,
    val lastSeenMillis: Long
)

@Entity(
    tableName = "listening_events",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"]), Index(value = ["eventType"]), Index(value = ["occurredAtMillis"])]
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val eventType: String,
    val positionMs: Long = 0,
    val occurredAtMillis: Long
)

@Entity(
    tableName = "track_availability",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"], unique = true), Index(value = ["state"])]
)
data class TrackAvailabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val state: String,
    val firstMissingAtMillis: Long? = null,
    val lastCheckedAtMillis: Long
)

@Entity(tableName = "scan_jobs", indices = [Index(value = ["status"]), Index(value = ["startedAtMillis"])])
data class ScanJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rootId: Long? = null,
    val type: String,
    val status: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val tracksFound: Int = 0,
    val newTracks: Int = 0,
    val updatedTracks: Int = 0,
    val excludedTracks: Int = 0,
    val failedTracks: Int = 0
)

@Entity(tableName = "scan_changes", indices = [Index(value = ["scanJobId"]), Index(value = ["changeType"])])
data class ScanChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanJobId: Long,
    val trackId: Long?,
    val mediaStoreId: Long?,
    val changeType: String,
    val note: String = ""
)

@Entity(tableName = "original_metadata", indices = [Index(value = ["trackId"], unique = true)])
data class OriginalMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int? = null,
    val createdAtMillis: Long
)

@Entity(tableName = "enriched_metadata", indices = [Index(value = ["trackId"]), Index(value = ["confidence"])])
data class EnrichedMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val key: String,
    val value: String,
    val source: String,
    val confidence: Float,
    val createdAtMillis: Long,
    val confirmed: Boolean = false
)

@Entity(tableName = "user_confirmed_metadata", indices = [Index(value = ["trackId"], unique = true)])
data class UserConfirmedMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val language: String? = null,
    val updatedAtMillis: Long
)

@Entity(tableName = "metadata_suggestions", indices = [Index(value = ["trackId"]), Index(value = ["status"])])
data class MetadataSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val field: String,
    val suggestedValue: String,
    val source: String,
    val confidence: Float,
    val status: String = "Pending",
    val createdAtMillis: Long
)

@Entity(tableName = "analysis_results", indices = [Index(value = ["trackId"]), Index(value = ["issueType"])])
data class AnalysisResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val issueType: String,
    val explanation: String,
    val severity: String,
    val ignored: Boolean = false,
    val createdAtMillis: Long
)

@Entity(tableName = "playlists", indices = [Index(value = ["name"])])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["trackId"]), Index(value = ["position"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
    val reason: String = ""
)

@Entity(tableName = "playlist_rules", indices = [Index(value = ["playlistId"])])
data class PlaylistRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val prompt: String,
    val interpretedRulesJson: String,
    val createdAtMillis: Long
)

@Entity(tableName = "export_jobs", indices = [Index(value = ["playlistId"]), Index(value = ["format"]), Index(value = ["status"])])
data class ExportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long?,
    val format: String,
    val destinationUri: String,
    val status: String,
    val createdAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val notes: String = ""
)

@Entity(tableName = "external_matches", indices = [Index(value = ["trackId"]), Index(value = ["provider"])])
data class ExternalMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val provider: String,
    val externalId: String,
    val url: String = "",
    val confidence: Float,
    val createdAtMillis: Long
)
