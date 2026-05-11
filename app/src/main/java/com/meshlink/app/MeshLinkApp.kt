package com.meshlink.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.meshlink.app.worker.MeshCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MeshLinkApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        scheduleCleanupWork()
    }

    /**
     * Schedules [MeshCleanupWorker] to run every 6 hours.
     * [ExistingPeriodicWorkPolicy.KEEP] means an existing schedule survives app restarts
     * without being reset — the 6-hour clock keeps ticking from the last execution.
     */
    private fun scheduleCleanupWork() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)  // skip cleanup if battery is critically low
            .build()
        val request = PeriodicWorkRequestBuilder<MeshCleanupWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "mesh_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Timber.d("MeshCleanupWorker scheduled (every 6h)")
    }
}
