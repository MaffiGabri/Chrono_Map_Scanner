package com.example.chronomapscanner.data.local.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class FileCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filePaths = inputData.getStringArray("FILE_PATHS")
        if (filePaths.isNullOrEmpty()) {
            return@withContext Result.success()
        }

        var allSuccess = true
        filePaths.forEach { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (!deleted) {
                        allSuccess = false
                    }
                }
                
                // Try to delete the corresponding thumbnail if it exists
                val thumbFile = File(file.parent, "thumb_${file.name}")
                if (thumbFile.exists()) {
                    thumbFile.delete()
                }
            } catch (e: Exception) {
                allSuccess = false
            }
        }

        if (allSuccess) Result.success() else Result.retry()
    }
}
