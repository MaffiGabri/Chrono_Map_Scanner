package com.example.skinhistoryscanner.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BackgroundDao {
    @Query("SELECT * FROM background_categories WHERE profileName = :profileName")
    fun getCategoriesForProfile(profileName: String): Flow<List<BackgroundCategoryEntity>>

    @Query("SELECT * FROM background_variants WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun getVariantsForCategory(categoryId: String): Flow<List<BackgroundVariantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: BackgroundCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: BackgroundVariantEntity)

    @Update
    suspend fun updateCategory(category: BackgroundCategoryEntity)

    @Update
    suspend fun updateVariant(variant: BackgroundVariantEntity)

    @Query("DELETE FROM background_categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: String)

    @Query("DELETE FROM background_variants WHERE id = :variantId")
    suspend fun deleteVariant(variantId: String)

    @Query("SELECT imagePath FROM background_variants WHERE categoryId IN (SELECT id FROM background_categories WHERE profileName = :profile) AND imagePath IS NOT NULL")
    suspend fun getVariantImagesByProfile(profile: String): List<String>
    
    @Query("UPDATE background_categories SET profileName = :newName WHERE profileName = :oldName")
    suspend fun renameProfileInCategories(oldName: String, newName: String)

    @Query("DELETE FROM background_categories WHERE profileName = :profileName")
    suspend fun deleteCategoriesByProfile(profileName: String)
    
    @Query("SELECT * FROM background_variants WHERE id = :variantId LIMIT 1")
    suspend fun getVariantById(variantId: String): BackgroundVariantEntity?
    
    @Query("SELECT * FROM background_variants WHERE id = :variantId LIMIT 1")
    fun getVariantByIdFlow(variantId: String): Flow<BackgroundVariantEntity?>
    
    @Query("SELECT * FROM background_variants WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    suspend fun getVariantsForCategorySync(categoryId: String): List<BackgroundVariantEntity>
    
    @Query("SELECT * FROM background_categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): BackgroundCategoryEntity?

    @Query("SELECT * FROM background_categories WHERE profileName = :profileName")
    suspend fun getCategoriesForProfileSync(profileName: String): List<BackgroundCategoryEntity>

    @Query("SELECT * FROM background_variants WHERE categoryId IN (SELECT id FROM background_categories WHERE profileName = :profileName)")
    suspend fun getVariantsForProfileSync(profileName: String): List<BackgroundVariantEntity>
}
