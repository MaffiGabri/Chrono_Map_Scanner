package com.example.chronomapscanner.data.repository

import com.example.chronomapscanner.data.domain.HistoryEntry
import com.example.chronomapscanner.data.domain.Mole
import com.example.chronomapscanner.data.domain.toDomain
import com.example.chronomapscanner.data.domain.toEntity
import com.example.chronomapscanner.data.local.room.MoleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation that uses Room as the offline data source.
 */
@Singleton
class OfflineMoleRepository @Inject constructor(
    private val moleDao: MoleDao,
    private val backgroundDao: com.example.chronomapscanner.data.local.room.BackgroundDao,
    private val fileRepository: FileRepository
) : MoleRepository {

    override suspend fun getMolesWithHistory(profileName: String): List<Mole> =
        withContext(Dispatchers.Default) {
            moleDao.getMolesWithHistory(profileName).map { moleWithHistory ->
                val domainMole = moleWithHistory.toDomain()
                domainMole.copy(
                    history = domainMole.history.map { entry ->
                        entry.copy(imagePath = fileRepository.getAbsolutePath(entry.imagePath))
                    }
                )
            }
        }

    override fun getMolesAtDate(profileName: String, targetDate: java.time.LocalDate): Flow<List<com.example.chronomapscanner.data.domain.MoleMapItem>> =
        moleDao.getMolesAtDate(profileName, targetDate).map { entities ->
            entities.map { entity ->
                val domainItem = entity.toDomain()
                domainItem.copy(imagePath = fileRepository.getAbsolutePath(domainItem.imagePath))
            }
        }.flowOn(Dispatchers.Default)

    override fun getAvailableDates(profileName: String): Flow<List<java.time.LocalDate>> =
        moleDao.getAvailableDates(profileName).flowOn(Dispatchers.Default)

    override fun getAvailableDatesForVariant(profileName: String, variantId: String): Flow<List<java.time.LocalDate>> =
        moleDao.getAvailableDatesForVariant(profileName, variantId).flowOn(Dispatchers.Default)

    override fun getMoleByIdWithHistory(moleId: String): Flow<Mole?> =
        moleDao.getMoleByIdWithHistory(moleId).map { entity ->
            val domainMole = entity?.toDomain()
            domainMole?.copy(
                history = domainMole.history.map { entry ->
                    entry.copy(imagePath = fileRepository.getAbsolutePath(entry.imagePath))
                }
            )
        }.flowOn(Dispatchers.Default)

    override fun getAllProfileNames(): Flow<List<String>> =
        moleDao.getAllProfileNames().flowOn(Dispatchers.Default)

    override fun getTotalMolesCount(): Flow<Int> = moleDao.getTotalMolesCount()

    override fun getMolesCountForProfile(profileName: String): Flow<Int> = moleDao.getMolesCountForProfile(profileName)

    override suspend fun insertMoleWithHistory(mole: Mole, historyEntry: HistoryEntry) {
        moleDao.insertMoleWithHistory(mole.toEntity(), historyEntry.toEntity())
    }

    override suspend fun upsertMole(mole: Mole) {
        moleDao.insertMole(mole.toEntity())
    }

    override suspend fun updateMolePosition(id: String, x: Float, y: Float, side: String) {
        moleDao.updateMolePosition(id, x, y, side)
    }

    override suspend fun updateMoleColor(id: String, color: String) {
        moleDao.updateMoleColor(id, color)
    }

    override suspend fun deleteMole(moleId: String) {
        val mole = moleDao.getMoleByIdWithHistory(moleId).firstOrNull()
        val imagePaths = mole?.history?.mapNotNull { fileRepository.getAbsolutePath(it.imagePath) } ?: emptyList()
        if (imagePaths.isNotEmpty()) {
            fileRepository.scheduleFileDeletion(imagePaths)
        }
        moleDao.deleteMole(moleId)
    }

    override suspend fun deleteMolesByProfile(profileName: String) {
        val historyImagePaths = moleDao.getHistoryImagePathsByProfile(profileName).mapNotNull { fileRepository.getAbsolutePath(it) }
        val variantImagePaths = backgroundDao.getVariantImagesByProfile(profileName).mapNotNull { fileRepository.getAbsolutePath(it) }
        val allPaths = historyImagePaths + variantImagePaths
        
        if (allPaths.isNotEmpty()) {
            fileRepository.scheduleFileDeletion(allPaths)
        }
        moleDao.deleteMolesByProfile(profileName)
        backgroundDao.deleteCategoriesByProfile(profileName)
    }

    override suspend fun renameProfile(oldName: String, newName: String) {
        moleDao.renameProfile(oldName, newName)
        backgroundDao.renameProfileInCategories(oldName, newName)
    }

    override suspend fun upsertHistoryEntry(entry: HistoryEntry) {
        moleDao.insertHistoryEntry(entry.toEntity())
    }

    override suspend fun deleteHistoryEntry(entryId: String) {
        val moleId = moleDao.getMoleIdForHistoryEntry(entryId)
        val imagePath = fileRepository.getAbsolutePath(moleDao.getHistoryEntryImagePath(entryId))
        if (imagePath != null) {
            fileRepository.scheduleFileDeletion(listOf(imagePath))
        }
        moleDao.deleteHistoryEntry(entryId)
        
        if (moleId != null) {
            val remaining = moleDao.getHistoryCountForMole(moleId)
            if (remaining == 0) {
                deleteMole(moleId)
            }
        }
    }

    override suspend fun deleteMolesByVariant(variantId: String) {
        val imagePaths = moleDao.getHistoryImagePathsByVariant(variantId).mapNotNull { fileRepository.getAbsolutePath(it) }
        if (imagePaths.isNotEmpty()) {
            fileRepository.scheduleFileDeletion(imagePaths)
        }
        moleDao.deleteMolesByVariant(variantId)
    }

    override suspend fun migrateMoles(oldVariantIds: List<String>, newVariantId: String) {
        if (oldVariantIds.isEmpty()) return
        moleDao.updateMolesVariant(oldVariantIds, newVariantId)
    }
}
