package com.audoneout.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.audoneout.app.data.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LibraryScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val repository: LibraryRepository
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result = runCatching {
        repository.scanMediaStore()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() }
    )

    companion object {
        const val UNIQUE_PERIODIC_WORK = "audoneout-periodic-library-scan"
        const val UNIQUE_INCREMENTAL_WORK = "audoneout-incremental-library-scan"
        const val TAG_INCREMENTAL = "incremental-library-scan"

        fun schedulePeriodic(context: Context, requireCharging: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresCharging(requireCharging)
                .build()
            val request = PeriodicWorkRequestBuilder<LibraryScanWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}

