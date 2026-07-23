package com.audoneout.app.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.audoneout.app.data.AudOneOutDatabase
import com.audoneout.app.data.LibraryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AudOneOutDatabase =
        Room.databaseBuilder(
            context,
            AudOneOutDatabase::class.java,
            "audoneout-library.db"
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7
            )
            .build()

    @Provides
    fun provideLibraryDao(database: AudOneOutDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `albums` (
                    `albumKey` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artistName` TEXT NOT NULL,
                    `trackCount` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `storageBytes` INTEGER NOT NULL,
                    `artworkUri` TEXT,
                    `lastAddedSeconds` INTEGER NOT NULL,
                    PRIMARY KEY(`albumKey`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_title` ON `albums` (`title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_artistName` ON `albums` (`artistName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_lastAddedSeconds` ON `albums` (`lastAddedSeconds`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `artists` (
                    `artistKey` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `trackCount` INTEGER NOT NULL,
                    `albumCount` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    PRIMARY KEY(`artistKey`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_artists_name` ON `artists` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_artists_albumCount` ON `artists` (`albumCount`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_artists_trackCount` ON `artists` (`trackCount`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `folders` (
                    `path` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `trackCount` INTEGER NOT NULL,
                    `storageBytes` INTEGER NOT NULL,
                    `included` INTEGER NOT NULL,
                    `lastSeenMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`path`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_included` ON `folders` (`included`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_trackCount` ON `folders` (`trackCount`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `listening_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    `eventType` TEXT NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `occurredAtMillis` INTEGER NOT NULL,
                    FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_trackId` ON `listening_events` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_eventType` ON `listening_events` (`eventType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_occurredAtMillis` ON `listening_events` (`occurredAtMillis`)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorite_tracks` (
                    `trackId` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `addedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`trackId`),
                    FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_tracks_trackId` ON `favorite_tracks` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_tracks_position` ON `favorite_tracks` (`position`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `radio_stations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `streamUrl` TEXT NOT NULL,
                    `homepageUrl` TEXT NOT NULL,
                    `artworkUrl` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `lastPlayedAtMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_radio_stations_streamUrl` ON `radio_stations` (`streamUrl`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_radio_stations_lastPlayedAtMillis` ON `radio_stations` (`lastPlayedAtMillis`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scrobble_queue` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    `startedAtSeconds` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `lastError` TEXT NOT NULL,
                    FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scrobble_queue_trackId` ON `scrobble_queue` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scrobble_queue_status` ON `scrobble_queue` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scrobble_queue_startedAtSeconds` ON `scrobble_queue` (`startedAtSeconds`)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `scrobble_queue`")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_confirmed_metadata` ADD COLUMN `albumArtist` TEXT")
            db.execSQL("ALTER TABLE `user_confirmed_metadata` ADD COLUMN `year` INTEGER")
            db.execSQL("ALTER TABLE `user_confirmed_metadata` ADD COLUMN `trackNumber` INTEGER")
            db.execSQL("ALTER TABLE `user_confirmed_metadata` ADD COLUMN `discNumber` INTEGER")
            db.execSQL("ALTER TABLE `user_confirmed_metadata` ADD COLUMN `mood` TEXT")
            db.execSQL("ALTER TABLE `user_confirmed_metadata` ADD COLUMN `energy` INTEGER")
            db.execSQL("ALTER TABLE `user_confirmed_metadata` ADD COLUMN `discoveryTags` TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `metadata_writebacks` (
                    `trackId` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `requestedAtMillis` INTEGER NOT NULL,
                    `completedAtMillis` INTEGER,
                    `writtenFields` TEXT NOT NULL,
                    `catalogOnlyFields` TEXT NOT NULL,
                    `errorMessage` TEXT NOT NULL,
                    PRIMARY KEY(`trackId`),
                    FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_writebacks_status` ON `metadata_writebacks` (`status`)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tracks` ADD COLUMN `volumeName` TEXT NOT NULL DEFAULT 'external_primary'")
            db.execSQL("DROP INDEX IF EXISTS `index_tracks_mediaStoreId`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_volumeName_mediaStoreId` ON `tracks` (`volumeName`, `mediaStoreId`)"
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_tracks_discoveryTags` ON `tracks` (`discoveryTags`)"
            )
        }
    }
}
