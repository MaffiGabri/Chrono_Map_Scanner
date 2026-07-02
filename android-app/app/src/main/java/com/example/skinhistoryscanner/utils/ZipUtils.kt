package com.example.skinhistoryscanner.utils

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    suspend fun zip(files: List<File>, zipFile: File) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        FileOutputStream(zipFile).use { fos ->
            zipToStream(files, fos)
        }
    }

    suspend fun zipToStream(
        files: List<File>, 
        outputStream: OutputStream, 
        onProgress: ((Int, Int) -> Unit)? = null
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val totalFiles = files.size
        ZipOutputStream(BufferedOutputStream(outputStream)).use { out ->
            for ((index, file) in files.withIndex()) {
                if (file.exists()) {
                    FileInputStream(file).use { fi ->
                        BufferedInputStream(fi).use { origin ->
                            val entry = ZipEntry(file.name)
                            out.putNextEntry(entry)
                            origin.copyTo(out)
                        }
                    }
                }
                onProgress?.invoke(index + 1, totalFiles)
            }
        }
    }

    suspend fun unzip(zipFile: File, targetDirectory: File) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(targetDirectory, entry.name)
                // Zip Slip protection: ensure the entry stays within the target directory
                if (!file.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator) &&
                    file.canonicalPath != targetDirectory.canonicalPath) {
                    throw SecurityException("Zip entry outside target directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
