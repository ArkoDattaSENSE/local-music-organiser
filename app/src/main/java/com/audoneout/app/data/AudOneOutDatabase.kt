package com.audoneout.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MusicRootEntity::class,
        FolderBlacklistRuleEntity::class,
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        FolderEntity::class,
        ListeningEventEntity::class,
        TrackAvailabilityEntity::class,
        ScanJobEntity::class,
        ScanChangeEntity::class,
        OriginalMetadataEntity::class,
        EnrichedMetadataEntity::class,
        UserConfirmedMetadataEntity::class,
        MetadataSuggestionEntity::class,
        AnalysisResultEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        PlaylistRuleEntity::class,
        ExportJobEntity::class,
        ExternalMatchEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AudOneOutDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
