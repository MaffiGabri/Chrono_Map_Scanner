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

                // Security Enhancement: Prevent path traversal by ensuring the file is within expected app directories
                val canonicalPath = file.canonicalPath
                val filesDirPath = applicationContext.filesDir.canonicalPath
                val cacheDirPath = applicationContext.cacheDir.canonicalPath

                if (!canonicalPath.startsWith(filesDirPath) && !canonicalPath.startsWith(cacheDirPath)) {
                    android.util.Log.w("FileCleanupWorker", "Security Warning: Attempted to delete a file outside allowed directories: $path")
                    return@forEach // Skip this file
                }

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
