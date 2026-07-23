package com.audoneout.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.audoneout.app.settings.AppSettings
import com.audoneout.app.worker.LibraryScanWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class AudOneOutApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: AppSettings

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            settings.state
                .map {
                    BackgroundCheckConfig(
                        enabled = it.automaticLibraryChecking,
                        frequency = it.checkFrequency,
                        chargingOnly = it.analyseOnlyWhileCharging,
                        quietMode = it.quietBackgroundMode
                    )
                }
                .distinctUntilChanged()
                .collect { config ->
                    if (config.enabled) {
                        LibraryScanWorker.schedulePeriodic(
                            context = this@AudOneOutApplication,
                            frequency = config.frequency,
                            chargingOnly = config.chargingOnly,
                            quietMode = config.quietMode
                        )
                    }
                    else LibraryScanWorker.cancelPeriodic(this@AudOneOutApplication)
                }
        }
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

private data class BackgroundCheckConfig(
    val enabled: Boolean,
    val frequency: String,
    val chargingOnly: Boolean,
    val quietMode: Boolean
)
