package com.example.skinhistoryscanner

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SkinHistoryScannerApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Initialization is handled by androidx.startup if WorkManagerInitializer is merged,
        // but since we override workManagerConfiguration, WorkManager automatically uses it on demand.
        // If we want manual initialization, we'd call:
        // androidx.work.WorkManager.initialize(this, workManagerConfiguration)
        
        val request = androidx.work.PeriodicWorkRequestBuilder<com.example.skinhistoryscanner.data.repository.DataIntegrityWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataIntegrityWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
