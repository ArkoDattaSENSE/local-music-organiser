package com.audoneout.app.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
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
        ).build()

    @Provides
    fun provideLibraryDao(database: AudOneOutDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver
}
