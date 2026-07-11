package com.example.chronomapscanner.data.local.workers

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.chronomapscanner.data.domain.AppDatabaseDto
import com.example.chronomapscanner.data.domain.ReminderSettings
import com.example.chronomapscanner.data.domain.UserSettings
import com.example.chronomapscanner.data.repository.BackupRepository
import com.example.chronomapscanner.data.repository.MoleRepository
import com.example.chronomapscanner.data.local.datastore.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.chronomapscanner.R

@HiltWorker
class ExportDatabaseWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val moleRepository: MoleRepository,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val backgroundDao: com.example.chronomapscanner.data.local.room.BackgroundDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val profileName = inputData.getString("profile_name") ?: return@withContext Result.failure()
        val uriString = inputData.getString("destination_uri") ?: return@withContext Result.failure()
        val destinationUri = Uri.parse(uriString)

        try {
            val domainMoles = moleRepository.getMolesWithHistory(profileName)
            val categories = backgroundDao.getCategoriesForProfileSync(profileName).map {
                com.example.chronomapscanner.data.domain.BackgroundCategoryDto(
                    id = it.id,
                    name = it.name,
                    isBuiltIn = it.isBuiltIn
                )
            }
            val variants = backgroundDao.getVariantsForProfileSync(profileName).map {
                com.example.chronomapscanner.data.domain.BackgroundVariantDto(
                    id = it.id,
                    categoryId = it.categoryId,
                    name = it.name,
                    imagePath = if (it.imagePath != null) java.io.File(it.imagePath).name else null,
                    orderIndex = it.orderIndex,
                    dateAdded = it.dateAdded.toString(),
                    notes = it.notes
                )
            }

            val rawProfileImage = settingsRepository.getProfileImageForProfile(profileName).first()
            val cleanProfileImage = if (rawProfileImage != null) java.io.File(rawProfileImage).name else null

            // domainMoles contain absolute paths due to repository rehydration. We need to collect them for zipping,
            // but store only filenames in the JSON.
            val absoluteImagePaths = domainMoles.flatMap { it.history }.flatMap { entry ->
                val paths = mutableListOf<String>()
                entry.imagePath?.let { originalPath ->
                    paths.add(originalPath)
                    val file = java.io.File(originalPath)
                    val parent = file.parent
                    val thumbPath = if (parent != null) "$parent/thumb_${file.name}" else "thumb_${file.name}"
                    paths.add(thumbPath)
                }
                paths
            } + listOfNotNull(if (rawProfileImage?.startsWith("/") == true) rawProfileImage else context.filesDir.absolutePath + "/" + rawProfileImage) +
                backgroundDao.getVariantsForProfileSync(profileName).mapNotNull { it.imagePath?.let { path -> if (path.startsWith("/")) path else context.filesDir.absolutePath + "/" + path } }
            
            val cleanDomainMoles = domainMoles.map { mole ->
                mole.copy(history = mole.history.map { entry ->
                    entry.copy(imagePath = if (entry.imagePath != null) java.io.File(entry.imagePath).name else null)
                })
            }

            val currentSettings = AppDatabaseDto(
                profileName = profileName,
                profileImage = cleanProfileImage,
                settings = UserSettings(
                    gender = settingsRepository.getGenderForProfile(profileName).first(),
                    bodyType = settingsRepository.getBodyTypeForProfile(profileName).first()
                ),
                reminders = ReminderSettings(
                    enabled = settingsRepository.remindersEnabled.first(),
                    intervalValue = settingsRepository.remindersValue.first(),
                    intervalUnit = settingsRepository.remindersUnit.first(),
                    lastReminderDate = settingsRepository.lastReminderDate.first()
                ),
                moles = cleanDomainMoles,
                colorSettings = settingsRepository.colorSettings.first(),
                categories = categories,
                variants = variants
            )
            
            val notificationId = 1001
            val channelId = "export_channel"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Esportazione Database", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Mostra il progresso dell'esportazione del database"
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
            
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.export_db))
                .setContentText("Preparazione in corso...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(100, 0, true)
                
            val notificationManager = NotificationManagerCompat.from(context)
            try {
                notificationManager.notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // Permesso non concesso su Android 13+, ignora silenziosamente
            }
            
            var lastUpdatePercent = -1
            backupRepository.createAndWriteExportZip(currentSettings, absoluteImagePaths, destinationUri) { current, total ->
                val percent = if (total > 0) (current * 100) / total else 0
                if (percent > lastUpdatePercent) {
                    lastUpdatePercent = percent
                    builder.setContentText("Esportazione: $current di $total")
                        .setProgress(total, current, false)
                    try {
                        notificationManager.notify(notificationId, builder.build())
                    } catch (e: SecurityException) {}
                }
            }
            
            val completeBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.export_db))
                .setContentText("Esportazione completata con successo!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            
            try {
                notificationManager.notify(notificationId, completeBuilder.build())
            } catch (e: SecurityException) {}
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            val errorBuilder = NotificationCompat.Builder(context, "export_channel")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.export_db))
                .setContentText("Errore durante l'esportazione")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            try {
                NotificationManagerCompat.from(context).notify(1001, errorBuilder.build())
            } catch (se: SecurityException) {}
            Result.failure()
        }
    }
}
