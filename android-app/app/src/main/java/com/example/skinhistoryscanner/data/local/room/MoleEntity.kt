package com.example.skinhistoryscanner.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import java.time.LocalDate

@Entity(
    tableName = "moles",
    indices = [
        Index(name = "idx_moles_profile_variant", value = ["profileName", "variantId"]),
        Index(name = "idx_moles_color", value = ["color"])
    ]
)
data class MoleEntity(
    @PrimaryKey val id: String,
    val profileName: String, // Per associare il neo a un profilo utente
    val x: Float,
    val y: Float,
    val variantId: String,
    val color: String
)
