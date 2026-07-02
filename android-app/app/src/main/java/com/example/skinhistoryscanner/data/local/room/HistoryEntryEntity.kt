package com.example.skinhistoryscanner.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo
import java.time.LocalDate

@Entity(
    tableName = "history_entries",
    foreignKeys = [
        ForeignKey(
            entity = MoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["mole_id"],
            onDelete = ForeignKey.CASCADE // Elimina automaticamente la cronologia se il neo viene eliminato
        )
    ],
    indices = [
        Index("mole_id"),
        Index(name = "idx_history_date", value = ["date"])
    ]
)
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "mole_id") val moleId: String,
    val date: LocalDate,
    val imagePath: String?,
    val notes: String?
)
