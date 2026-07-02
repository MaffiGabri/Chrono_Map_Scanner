package com.example.skinhistoryscanner.data.local.workers

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.skinhistoryscanner.data.domain.Mole as DomainMole
import com.example.skinhistoryscanner.data.domain.HistoryEntry as DomainHistoryEntry
import com.example.skinhistoryscanner.data.repository.BackupRepository
import com.example.skinhistoryscanner.data.repository.MoleRepository
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import com.example.skinhistoryscanner.data.local.room.AppDatabaseRoom
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

@HiltWorker
class ImportDatabaseWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val moleRepository: MoleRepository,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val database: AppDatabaseRoom,
    private val backgroundDao: com.example.skinhistoryscanner.data.local.room.BackgroundDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriString = inputData.getString("import_uri") ?: return@withContext Result.failure()
        val modeString = inputData.getString("mode") ?: return@withContext Result.failure()
        val newName = inputData.getString("new_name")
        val importUri = Uri.parse(uriString)
        
        settingsRepository.isImporting.value = true
        try {
            val currentProfile = settingsRepository.currentProfile.first()

            backupRepository.extractImportZip(importUri) { importedDb, tempDir ->
                val finalProfileName = when (modeString) {
                    "OVERWRITE" -> currentProfile
                    "NEW_PROFILE" -> newName ?: importedDb.profileName
                    else -> currentProfile
                }

                if (modeString == "OVERWRITE") {
                    moleRepository.deleteMolesByProfile(finalProfileName)
                    backgroundDao.deleteCategoriesByProfile(finalProfileName)
                }
                
                database.withTransaction {
                    val isNewProfile = modeString == "NEW_PROFILE"
                    
                    val oldToNewCategoryIds = mutableMapOf<String, String>()
                    val oldToNewVariantIds = mutableMapOf<String, String>()
                    
                    importedDb.categories.forEach { category ->
                        val finalCategoryId = if (isNewProfile) java.util.UUID.randomUUID().toString() else category.id
                        oldToNewCategoryIds[category.id] = finalCategoryId
                        
                        backgroundDao.insertCategory(
                            com.example.skinhistoryscanner.data.local.room.BackgroundCategoryEntity(
                                id = finalCategoryId,
                                profileName = finalProfileName,
                                name = category.name,
                                isBuiltIn = category.isBuiltIn
                            )
                        )
                    }

                    importedDb.variants.forEach { variant ->
                        val finalVariantId = if (isNewProfile) java.util.UUID.randomUUID().toString() else variant.id
                        oldToNewVariantIds[variant.id] = finalVariantId
                        
                        val finalCategoryId = oldToNewCategoryIds[variant.categoryId] ?: variant.categoryId
                        val newImageName = if (isNewProfile && variant.imagePath != null) "bg_${finalVariantId}.jpg" else null
                        val finalImagePath = backupRepository.importImageToInternalStorage(tempDir, variant.imagePath, newImageName)
                        
                        backgroundDao.insertVariant(
                            com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity(
                                id = finalVariantId,
                                categoryId = finalCategoryId,
                                name = variant.name,
                                imagePath = finalImagePath,
                                orderIndex = variant.orderIndex,
                                dateAdded = java.time.LocalDate.parse(variant.dateAdded),
                                notes = variant.notes
                            )
                        )
                    }

                    importedDb.moles.forEach { mole ->
                        val finalMoleId = if (isNewProfile) java.util.UUID.randomUUID().toString() else mole.id
                        val mappedVariantId = oldToNewVariantIds[mole.side] ?: mole.side
                        val domainMole = DomainMole(
                            id = finalMoleId,
                            profileName = finalProfileName,
                            x = mole.x,
                            y = mole.y,
                            side = mappedVariantId,
                            color = mole.color
                        )
                        moleRepository.upsertMole(domainMole)
                        
                        mole.history.forEach { entry ->
                            val finalEntryId = if (isNewProfile) java.util.UUID.randomUUID().toString() else entry.id
                            val newImageName = if (isNewProfile && entry.imagePath != null) "img_$finalEntryId.jpg" else null
                            val finalImagePath = backupRepository.importImageToInternalStorage(tempDir, entry.imagePath, newImageName)
                            moleRepository.upsertHistoryEntry(
                                DomainHistoryEntry(
                                    id = finalEntryId,
                                    moleId = finalMoleId,
                                    date = entry.date,
                                    imagePath = finalImagePath,
                                    notes = entry.notes
                                )
                            )
                        }
                    }
                }
                
                settingsRepository.updateBodySettings(
                    importedDb.settings.gender,
                    importedDb.settings.bodyType,
                    finalProfileName
                )
                
                if (modeString == "OVERWRITE") {
                    settingsRepository.updateReminderSettings(
                        importedDb.reminders.enabled,
                        importedDb.reminders.intervalValue,
                        importedDb.reminders.intervalUnit,
                        importedDb.reminders.lastReminderDate
                    )
                }
                
                val isNewProfile = modeString == "NEW_PROFILE"
                val profileImageName = if (isNewProfile && importedDb.profileImage != null) "profile_${java.util.UUID.randomUUID()}.jpg" else null
                settingsRepository.setProfileImage(
                    backupRepository.importImageToInternalStorage(tempDir, importedDb.profileImage, profileImageName),
                    finalProfileName
                )
                
                settingsRepository.setCurrentProfile(finalProfileName)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        } finally {
            settingsRepository.isImporting.value = false
        }
    }
}
