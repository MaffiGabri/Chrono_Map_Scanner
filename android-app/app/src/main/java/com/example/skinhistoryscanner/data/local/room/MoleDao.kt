package com.example.skinhistoryscanner.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class MoleMapDto(
    val id: String,
    val x: Float,
    val y: Float,
    val variantId: String,
    val color: String,
    val historyDate: LocalDate?,
    val imagePath: String?
)

@Dao
interface MoleDao {
    @Transaction
    @Query("SELECT * FROM moles WHERE profileName = :profile")
    suspend fun getMolesWithHistory(profile: String): List<MoleWithHistory>

    @Query("""
        SELECT m.id, m.x, m.y, m.variantId, m.color, h.date as historyDate, h.imagePath 
        FROM moles m 
        LEFT JOIN history_entries h ON m.id = h.mole_id 
            AND h.date = (SELECT MAX(date) FROM history_entries WHERE mole_id = m.id AND date <= :targetDate)
        WHERE m.profileName = :profile
        AND (h.date IS NOT NULL OR NOT EXISTS (SELECT 1 FROM history_entries WHERE mole_id = m.id))
    """)
    fun getMolesAtDate(profile: String, targetDate: LocalDate): Flow<List<MoleMapDto>>

    @Query("SELECT DISTINCT h.date FROM history_entries h JOIN moles m ON h.mole_id = m.id WHERE m.profileName = :profile ORDER BY h.date ASC")
    fun getAvailableDates(profile: String): Flow<List<LocalDate>>

    @Query("SELECT DISTINCT h.date FROM history_entries h JOIN moles m ON h.mole_id = m.id WHERE m.profileName = :profile AND m.variantId = :variantId ORDER BY h.date ASC")
    fun getAvailableDatesForVariant(profile: String, variantId: String): Flow<List<LocalDate>>

    @Query("SELECT imagePath FROM history_entries WHERE imagePath IS NOT NULL")
    suspend fun getAllActiveImagePathsSync(): List<String>

    @Transaction
    @Query("SELECT * FROM moles WHERE id = :moleId")
    fun getMoleByIdWithHistory(moleId: String): Flow<MoleWithHistory?>

    @Query("SELECT DISTINCT profileName FROM moles")
    fun getAllProfileNames(): Flow<List<String>>

    @Query("SELECT DISTINCT profileName FROM moles")
    suspend fun getAllProfileNamesSync(): List<String>

    @Upsert
    suspend fun insertMole(mole: MoleEntity)

    @Update
    suspend fun updateMole(mole: MoleEntity)

    @Query("UPDATE moles SET x = :x, y = :y, variantId = :variantId WHERE id = :id")
    suspend fun updateMolePosition(id: String, x: Float, y: Float, variantId: String)

    @Query("UPDATE moles SET color = :color WHERE id = :id")
    suspend fun updateMoleColor(id: String, color: String)

    @Query("DELETE FROM moles WHERE id = :moleId")
    suspend fun deleteMole(moleId: String)

    @Query("DELETE FROM moles WHERE profileName = :profileName")
    suspend fun deleteMolesByProfile(profileName: String)

    @Query("SELECT h.imagePath FROM history_entries h JOIN moles m ON h.mole_id = m.id WHERE m.profileName = :profile AND h.imagePath IS NOT NULL")
    suspend fun getHistoryImagePathsByProfile(profile: String): List<String>



    @Query("SELECT COUNT(*) FROM moles")
    fun getTotalMolesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM moles WHERE profileName = :profileName")
    fun getMolesCountForProfile(profileName: String): Flow<Int>

    @Query("UPDATE moles SET profileName = :newName WHERE profileName = :oldName")
    suspend fun renameProfile(oldName: String, newName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: HistoryEntryEntity)

    @Update
    suspend fun updateHistoryEntry(entry: HistoryEntryEntity)

    @Query("SELECT imagePath FROM history_entries WHERE id = :entryId")
    suspend fun getHistoryEntryImagePath(entryId: String): String?

    @Query("SELECT mole_id FROM history_entries WHERE id = :entryId")
    suspend fun getMoleIdForHistoryEntry(entryId: String): String?

    @Query("SELECT COUNT(*) FROM history_entries WHERE mole_id = :moleId")
    suspend fun getHistoryCountForMole(moleId: String): Int

    @Query("DELETE FROM history_entries WHERE id = :entryId")
    suspend fun deleteHistoryEntry(entryId: String)

    @Query("SELECT h.imagePath FROM history_entries h JOIN moles m ON h.mole_id = m.id WHERE m.variantId = :variantId AND h.imagePath IS NOT NULL")
    suspend fun getHistoryImagePathsByVariant(variantId: String): List<String>

    @Query("DELETE FROM moles WHERE variantId = :variantId")
    suspend fun deleteMolesByVariant(variantId: String)

    @Query("UPDATE moles SET variantId = :newVariantId WHERE variantId IN (:oldVariantIds)")
    suspend fun updateMolesVariant(oldVariantIds: List<String>, newVariantId: String)

    @Query("SELECT * FROM moles WHERE variantId IN (:variantIds)")
    suspend fun getMolesByVariantsSync(variantIds: List<String>): List<MoleEntity>

    @Transaction
    suspend fun insertMoleWithHistory(mole: MoleEntity, entry: HistoryEntryEntity) {
        insertMole(mole)
        insertHistoryEntry(entry)
    }
}

data class MoleWithHistory(
    @Embedded val mole: MoleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mole_id"
    )
    val history: List<HistoryEntryEntity>
)


