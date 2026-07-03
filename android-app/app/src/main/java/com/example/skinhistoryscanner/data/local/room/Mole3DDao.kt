package com.example.skinhistoryscanner.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class Mole3DMapDto(
    val id: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val modelId: String,
    val color: String,
    val historyDate: LocalDate?,
    val imagePath: String?
)

@Dao
interface Mole3DDao {
    @Query("""
        SELECT m.id, m.x, m.y, m.z, m.modelId, m.color, h.date as historyDate, h.imagePath
        FROM moles_3d m
        LEFT JOIN history_entries_3d h ON m.id = h.mole_3d_id
            AND h.date = (SELECT MAX(date) FROM history_entries_3d WHERE mole_3d_id = m.id AND date <= :targetDate)
        WHERE m.profileName = :profile
        AND (h.date IS NOT NULL OR NOT EXISTS (SELECT 1 FROM history_entries_3d WHERE mole_3d_id = m.id))
    """)
    fun getMolesAtDate(profile: String, targetDate: LocalDate): Flow<List<Mole3DMapDto>>

    @Upsert
    suspend fun insertMole(mole: Mole3DEntity)

    @Update
    suspend fun updateMole(mole: Mole3DEntity)

    @Query("UPDATE moles_3d SET x = :x, y = :y, z = :z WHERE id = :id")
    suspend fun updateMolePosition(id: String, x: Float, y: Float, z: Float)

    @Query("DELETE FROM moles_3d WHERE id = :moleId")
    suspend fun deleteMole(moleId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: HistoryEntry3DEntity)
}
