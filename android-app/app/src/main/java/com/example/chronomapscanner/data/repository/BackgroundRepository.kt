package com.example.chronomapscanner.data.repository

import com.example.chronomapscanner.data.local.room.BackgroundCategoryEntity
import com.example.chronomapscanner.data.local.room.BackgroundDao
import com.example.chronomapscanner.data.local.room.BackgroundVariantEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate
import java.util.UUID

@Singleton
class BackgroundRepository @Inject constructor(
    private val backgroundDao: BackgroundDao,
    private val fileRepository: FileRepository,
    private val moleRepository: MoleRepository
) {

    suspend fun ensureBuiltInCategoriesExist(profileName: String) {
        val categories = backgroundDao.getCategoriesForProfileSync(profileName)
        
        var personaCat = categories.find { it.name == "Persona" }
        if (personaCat == null) {
            val categoryId = "cat_persona_$profileName"
            personaCat = BackgroundCategoryEntity(
                id = categoryId,
                profileName = profileName,
                name = "Persona",
                isBuiltIn = true
            )
            backgroundDao.insertCategory(personaCat)
            backgroundDao.insertVariant(
                BackgroundVariantEntity(
                    id = "FRONT_$profileName",
                    categoryId = categoryId,
                    name = "Fronte",
                    imagePath = null,
                    orderIndex = 0,
                    dateAdded = LocalDate.now(),
                    notes = null
                )
            )
            backgroundDao.insertVariant(
                BackgroundVariantEntity(
                    id = "BACK_$profileName",
                    categoryId = categoryId,
                    name = "Retro",
                    imagePath = null,
                    orderIndex = 1,
                    dateAdded = LocalDate.now(),
                    notes = null
                )
            )
        }

        var customCat = categories.find { it.name == "Personalizzato" }
        if (customCat == null) {
            val categoryId = "cat_custom_$profileName"
            customCat = BackgroundCategoryEntity(
                id = categoryId,
                profileName = profileName,
                name = "Personalizzato",
                isBuiltIn = true // Segnamo come built-in per evitare l'eliminazione accidentale della categoria
            )
            backgroundDao.insertCategory(customCat)
        }
    }

    fun getCategoriesForProfile(profileName: String): Flow<List<BackgroundCategoryEntity>> =
        backgroundDao.getCategoriesForProfile(profileName).flowOn(Dispatchers.Default)

    fun getVariantsForCategory(categoryId: String): Flow<List<BackgroundVariantEntity>> {
        return backgroundDao.getVariantsForCategory(categoryId).map { list ->
            list.map { it.copy(imagePath = fileRepository.getAbsolutePath(it.imagePath)) }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun getVariantsForCategorySync(categoryId: String): List<BackgroundVariantEntity> {
        return backgroundDao.getVariantsForCategorySync(categoryId).map {
            it.copy(imagePath = fileRepository.getAbsolutePath(it.imagePath))
        }
    }

    fun getVariantByIdFlow(variantId: String): Flow<BackgroundVariantEntity?> {
        return backgroundDao.getVariantByIdFlow(variantId).map { entity ->
            entity?.copy(imagePath = fileRepository.getAbsolutePath(entity.imagePath))
        }.flowOn(Dispatchers.Default)
    }

    suspend fun insertCategory(category: BackgroundCategoryEntity) {
        backgroundDao.insertCategory(category)
    }

    suspend fun insertVariant(variant: BackgroundVariantEntity) {
        backgroundDao.insertVariant(variant)
    }

    suspend fun updateCategory(category: BackgroundCategoryEntity) {
        backgroundDao.updateCategory(category)
    }

    suspend fun updateVariant(variant: BackgroundVariantEntity) {
        backgroundDao.updateVariant(variant)
    }

    suspend fun deleteCategory(categoryId: String) {
        val variants = backgroundDao.getVariantsForCategorySync(categoryId)
        val imagePaths = variants.mapNotNull { fileRepository.getAbsolutePath(it.imagePath) }
        if (imagePaths.isNotEmpty()) {
            fileRepository.scheduleFileDeletion(imagePaths)
        }
        variants.forEach {
            moleRepository.deleteMolesByVariant(it.id)
        }
        backgroundDao.deleteCategory(categoryId)
    }

    suspend fun deleteVariant(variantId: String) {
        val variant = backgroundDao.getVariantById(variantId)
        val imagePath = fileRepository.getAbsolutePath(variant?.imagePath)
        if (imagePath != null) {
            fileRepository.scheduleFileDeletion(listOf(imagePath))
        }
        moleRepository.deleteMolesByVariant(variantId)
        backgroundDao.deleteVariant(variantId)
    }
}
