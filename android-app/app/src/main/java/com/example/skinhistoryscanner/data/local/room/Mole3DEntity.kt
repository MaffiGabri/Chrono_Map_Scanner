package com.example.skinhistoryscanner.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey
import java.time.LocalDate

@Entity(
    tableName = "moles_3d",
    indices = [
        Index(name = "idx_moles_3d_profile_model", value = ["profileName", "modelId"]),
        Index(name = "idx_moles_3d_color", value = ["color"])
    ]
)
data class Mole3DEntity(
    @PrimaryKey val id: String,
    val profileName: String, // Per associare il neo a un profilo utente
    val x: Float,
    val y: Float,
    val z: Float,
    val modelId: String,
    val color: String
)

@Entity(
    tableName = "history_entries_3d",
    foreignKeys = [
        ForeignKey(
            entity = Mole3DEntity::class,
            parentColumns = ["id"],
            childColumns = ["mole_3d_id"],
            onDelete = ForeignKey.CASCADE // Elimina automaticamente la cronologia se il neo viene eliminato
        )
    ],
    indices = [
        Index("mole_3d_id"),
        Index(name = "idx_history_3d_date", value = ["date"])
    ]
)
data class HistoryEntry3DEntity(
    @PrimaryKey val id: String,
    val mole_3d_id: String,
    val date: LocalDate,
    val imagePath: String?,
    val notes: String?
)
