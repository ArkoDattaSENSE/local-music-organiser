package com.audoneout.app.scan

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.audoneout.app.worker.LibraryScanWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context
) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val workManager get() = WorkManager.getInstance(context)

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
            .addTag(LibraryScanWorker.TAG_INCREMENTAL)
            .build()
        workManager.enqueueUniqueWork(
            LibraryScanWorker.UNIQUE_INCREMENTAL_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun register() {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }
}

