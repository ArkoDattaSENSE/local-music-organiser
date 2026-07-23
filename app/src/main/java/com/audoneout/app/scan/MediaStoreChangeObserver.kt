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
import com.audoneout.app.settings.AppSettings
import com.audoneout.app.worker.LibraryScanWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class MediaStoreChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: AppSettings
) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val workManager get() = WorkManager.getInstance(context)
    @Volatile private var automaticChecksEnabled = true

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.state.collect { automaticChecksEnabled = it.automaticLibraryChecking }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (!automaticChecksEnabled) return
        val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
            .setInitialDelay(20, TimeUnit.SECONDS)
            .addTag(LibraryScanWorker.TAG_INCREMENTAL)
            .build()
        workManager.enqueueUniqueWork(
            LibraryScanWorker.UNIQUE_INCREMENTAL_WORK,
            ExistingWorkPolicy.REPLACE,
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
