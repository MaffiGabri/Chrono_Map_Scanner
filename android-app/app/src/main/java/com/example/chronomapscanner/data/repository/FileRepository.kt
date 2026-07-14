package com.example.chronomapscanner.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun saveImageFromUri(uri: Uri, prefix: String = "img"): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${prefix}_${UUID.randomUUID()}.jpg"
            val destFile = File(context.filesDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Generate thumbnail
            try {
                val thumbFile = File(context.filesDir, "thumb_$fileName")
                
                val options = android.graphics.BitmapFactory.Options()
                options.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeFile(destFile.absolutePath, options)
                
                val reqSize = 150
                var inSampleSize = 1
                if (options.outHeight > reqSize || options.outWidth > reqSize) {
                    val halfHeight: Int = options.outHeight / 2
                    val halfWidth: Int = options.outWidth / 2
                    while (halfHeight / inSampleSize >= reqSize && halfWidth / inSampleSize >= reqSize) {
                        inSampleSize *= 2
                    }
                }
                
                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                
                val bitmap = android.graphics.BitmapFactory.decodeFile(destFile.absolutePath, options)
                if (bitmap != null) {
                    thumbFile.outputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Do not fail the whole save if thumbnail generation fails
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)

            // Security Enhancement: Prevent path traversal by ensuring the file is within expected app directories
            val canonicalPath = file.canonicalPath
            val filesDirPath = context.filesDir.canonicalPath
            val cacheDirPath = context.cacheDir.canonicalPath

            if (!canonicalPath.startsWith(filesDirPath) && !canonicalPath.startsWith(cacheDirPath)) {
                android.util.Log.w("FileRepository", "Security Warning: Attempted to delete a file outside allowed directories: $path")
                return@withContext
            }

            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun copyFile(sourcePath: String, destFile: File): String? = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            if (source.exists()) {
                source.copyTo(destFile, overwrite = true)
                destFile.absolutePath
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun scheduleFileDeletion(paths: List<String>) {
        if (paths.isEmpty()) return
        val workManager = androidx.work.WorkManager.getInstance(context)
        val data = androidx.work.Data.Builder()
            .putStringArray("FILE_PATHS", paths.toTypedArray())
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<com.example.chronomapscanner.data.local.workers.FileCleanupWorker>()
            .setInputData(data)
            .build()
        workManager.enqueue(request)
    }
}
