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
            .addMigrations(MIGRATION_1_2)
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
}
