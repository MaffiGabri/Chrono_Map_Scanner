package com.example.chronomapscanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.chronomapscanner.data.local.datastore.SettingsRepository
import com.example.chronomapscanner.data.local.room.BackgroundDao
import com.example.chronomapscanner.data.local.room.MoleDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class DataIntegrityWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val moleDao: MoleDao,
    private val backgroundDao: BackgroundDao
) : CoroutineWorker(context, workerParams) {

    private val TAG = "DataIntegrityWorker"

    override suspend fun doWork(): Result {
        return try {
            val intervalMin = settingsRepository.scannerIntervalMin.first()
            if (intervalMin <= 0) {
                Log.d(TAG, "Scanner disabilitato. Nessuna azione eseguita.")
                return Result.success()
            }

            val delayMs = settingsRepository.scannerDelayMs.first()
            val profile = settingsRepository.currentProfile.first()

            Log.d(TAG, "Inizio scansione integrità per il profilo: $profile")
            runProfileScanCycle(profile, delayMs)

            Log.d(TAG, "Scansione profilo completata. Inizio pulizia file orfani (cross-profile)...")
            cleanupOrphanedFiles(delayMs)

            Log.d(TAG, "DataIntegrityWorker ciclo completato con successo.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante DataIntegrityWorker", e)
            Result.retry()
        }
    }

    private suspend fun runProfileScanCycle(profile: String, delayBetweenMolesMs: Long) {
        val variants = backgroundDao.getVariantsForProfileSync(profile)
        val validVariantIds = variants.map { it.id }.toSet()

        val moles = moleDao.getMolesWithHistory(profile)

        for (mole in moles) {
            if (isStopped) break

            // 1. Verifica Variante
            if (!validVariantIds.contains(mole.mole.variantId)) {
                Log.w(TAG, "Neo ${mole.mole.id} appartiene a una variante inesistente (${mole.mole.variantId}). Eliminazione neo.")
                moleDao.deleteMole(mole.mole.id)
                continue
            }

            // 2. Verifica Cronologia Vuota
            if (mole.history.isEmpty()) {
                Log.w(TAG, "Neo ${mole.mole.id} non ha nessuna storia. Eliminazione neo.")
                moleDao.deleteMole(mole.mole.id)
                continue
            }

            // 3. Verifica Foto Disperse e Anteprime
            for (entry in mole.history) {
                if (entry.imagePath != null) {
                    val file = File(entry.imagePath)
                    if (!file.exists()) {
                        Log.w(TAG, "Immagine ${entry.imagePath} mancante per l'entry ${entry.id}.")
                        if (entry.notes.isNullOrBlank()) {
                            Log.w(TAG, "L'entry ${entry.id} non ha note e la foto è mancante. Eliminazione entry.")
                            moleDao.deleteHistoryEntry(entry.id)
                        } else {
                            Log.w(TAG, "Rimozione del riferimento all'immagine mancante nell'entry ${entry.id}.")
                            moleDao.updateHistoryEntry(entry.copy(imagePath = null))
                        }
                    } else {
                        // Verifica e genera thumbnail
                        val thumbFile = File(file.parent, "thumb_${file.name}")
                        if (!thumbFile.exists()) {
                            Log.d(TAG, "Generazione thumbnail mancante per ${file.name}")
                            generateThumbnail(file, thumbFile)
                        }
                    }
                } else if (entry.notes.isNullOrBlank()) {
                    Log.w(TAG, "L'entry ${entry.id} è completamente vuota. Eliminazione entry.")
                    moleDao.deleteHistoryEntry(entry.id)
                }
            }

            delay(delayBetweenMolesMs)
        }

        val profileImage = settingsRepository.getProfileImageForProfile(profile).first()
        if (profileImage != null) {
            val f = File(profileImage)
            if (!f.exists()) {
                Log.w(TAG, "Immagine profilo mancante per $profile. Reset.")
                settingsRepository.setProfileImage(null, profile)
            }
        }

        for (variant in variants) {
            if (variant.imagePath != null) {
                val f = File(variant.imagePath)
                if (!f.exists()) {
                    Log.w(TAG, "Immagine variante mancante per variante ${variant.id}. Reset.")
                    backgroundDao.updateVariant(variant.copy(imagePath = null))
                }
            }
        }
    }

    private suspend fun cleanupOrphanedFiles(delayMs: Long) {
        val filesDir = context.filesDir
        if (filesDir == null || !filesDir.exists()) return

        val allFiles = filesDir.listFiles() ?: return
        if (allFiles.isEmpty()) return

        val activePaths = mutableSetOf<String>()
        val allProfiles = moleDao.getAllProfileNamesSync().toSet() + settingsRepository.currentProfile.first()
        
        for (prof in allProfiles) {
            val pi = settingsRepository.getProfileImageForProfile(prof).first()
            if (pi != null) activePaths.add(pi)

            val variants = backgroundDao.getVariantsForProfileSync(prof)
            for (v in variants) {
                if (v.imagePath != null) activePaths.add(v.imagePath)
            }
            delay(10)
        }

        val allImagePaths = moleDao.getAllActiveImagePathsSync()
        for (path in allImagePaths) {
            activePaths.add(path)
            val f = File(path)
            val thumbPath = File(f.parent, "thumb_${f.name}").absolutePath
            activePaths.add(thumbPath)
        }

        val currentTime = System.currentTimeMillis()
        val safeThresholdMs = 15 * 60 * 1000
        
        for (file in allFiles) {
            if (isStopped) break

            val name = file.name
            if (!name.startsWith("edited_") && !name.startsWith("imported_") && 
                !name.startsWith("thumb_") && !name.startsWith("profile_") &&
                !name.startsWith("camera_") && !name.startsWith("variant_")) {
                continue 
            }

            val fileAge = currentTime - file.lastModified()
            if (fileAge < safeThresholdMs) {
                continue
            }

            if (!activePaths.contains(file.absolutePath)) {
                Log.w(TAG, "Pulizia file orfano: ${file.absolutePath}")
                try {
                    file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Impossibile eliminare file orfano: ${file.absolutePath}", e)
                }
            }
            delay(delayMs)
        }
    }

    private suspend fun generateThumbnail(originalFile: File, thumbFile: File) {
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(originalFile.absolutePath, options)
                
                val reqSize = 150
                val aspect = if (options.outWidth > 0) options.outHeight.toFloat() / options.outWidth else 1f
                val thumbW = if (aspect > 1f) (reqSize / aspect).toInt() else reqSize
                val thumbH = if (aspect > 1f) reqSize else (reqSize * aspect).toInt()

                options.inSampleSize = calculateInSampleSize(options, thumbW, thumbH)
                options.inJustDecodeBounds = false

                val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, options) ?: return@withContext
                
                val exif = ExifInterface(originalFile.absolutePath)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
                
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                val scaled = Bitmap.createScaledBitmap(rotated, thumbW.coerceAtLeast(1), thumbH.coerceAtLeast(1), true)

                FileOutputStream(thumbFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }

                if (rotated != bitmap) bitmap.recycle()
                if (scaled != rotated) rotated.recycle()
                scaled.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Errore nella rigenerazione della thumbnail per ${originalFile.name}", e)
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
