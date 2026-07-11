package com.example.chronomapscanner.data.repository

import android.content.Context
import android.net.Uri
import com.example.chronomapscanner.utils.ZipUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.chronomapscanner.data.domain.AppDatabaseDto

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: FileRepository
) {

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true 
    }

    suspend fun createAndWriteExportZip(
        databaseDto: AppDatabaseDto,
        imagePaths: List<String>,
        destinationUri: Uri,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "export_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            val jsonFile = File(tempDir, "Skin History Scanner_db.json")
            val jsonContent = json.encodeToString(databaseDto)
            jsonFile.writeText(jsonContent)

            val filesToZip = mutableListOf(jsonFile)

            imagePaths.distinct().forEach { path ->
                val imageFile = File(path)
                // Evita di inserire più file con lo stesso nome nell'archivio (ZipException)
                if (imageFile.exists() && filesToZip.none { it.name == imageFile.name }) {
                    filesToZip.add(imageFile)
                }
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                ZipUtils.zipToStream(filesToZip, output, onProgress)
            }

            tempDir.deleteRecursively()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun extractImportZip(
        importUri: Uri,
        onExtracted: suspend (AppDatabaseDto, File) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempZipFile = File(context.cacheDir, "temp_import.zip")
            context.contentResolver.openInputStream(importUri)?.use { input ->
                tempZipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val tempDir = File(context.cacheDir, "unzip_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            ZipUtils.unzip(tempZipFile, tempDir)

            val jsonFile = File(tempDir, "Skin History Scanner_db.json")
            if (jsonFile.exists()) {
                val importedDb = json.decodeFromString<AppDatabaseDto>(jsonFile.readText())
                onExtracted(importedDb, tempDir)
            }

            tempDir.deleteRecursively()
            tempZipFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importImageToInternalStorage(tempDir: File, originalPath: String?, newFileName: String? = null): String? = withContext(Dispatchers.IO) {
        if (originalPath == null) return@withContext null
        try {
            val sourceImage = File(tempDir, File(originalPath).name)
            if (sourceImage.exists()) {
                val destImage = File(context.filesDir, newFileName ?: sourceImage.name)
                sourceImage.copyTo(destImage, overwrite = true)
                destImage.name
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
