package com.audoneout.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.audoneout.app.MainActivity
import com.audoneout.app.R
import com.audoneout.app.data.LibraryRepository
import com.audoneout.app.domain.ScanProgress
import com.audoneout.app.settings.AppSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class LibraryScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val repository: LibraryRepository,
    private val settings: AppSettings
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        if (!settings.state.first().automaticLibraryChecking) return Result.success()
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(applicationContext, audioPermission) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        return try {
            val progress = repository.scanMediaStore()
            notifyAboutNewTracks(progress)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private suspend fun notifyAboutNewTracks(progress: ScanProgress) {
        if (progress.newTracks <= 0 || !settings.state.first().notifyWhenNewTracksReady) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (!notificationManager.areNotificationsEnabled()) return

        val systemNotificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        systemNotificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Library checks",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Useful results from background music-library checks"
            }
        )

        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val trackLabel = if (progress.newTracks == 1) "track" else "tracks"
        notificationManager.notify(
            NEW_TRACKS_NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New music is ready")
                .setContentText("Found ${progress.newTracks} new $trackLabel to review")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "audoneout-periodic-library-scan"
        const val UNIQUE_INCREMENTAL_WORK = "audoneout-incremental-library-scan"
        const val TAG_INCREMENTAL = "incremental-library-scan"
        private const val NOTIFICATION_CHANNEL_ID = "library-check-results"
        private const val NEW_TRACKS_NOTIFICATION_ID = 1001

        fun schedulePeriodic(
            context: Context,
            frequency: String = "Daily",
            chargingOnly: Boolean = true,
            quietMode: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresCharging(chargingOnly)
                .setRequiresDeviceIdle(quietMode)
                .build()
            val intervalHours = if (frequency.equals("Weekly", ignoreCase = true)) 7L * 24L else 24L
            val request = PeriodicWorkRequestBuilder<LibraryScanWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
        }
    }
}
