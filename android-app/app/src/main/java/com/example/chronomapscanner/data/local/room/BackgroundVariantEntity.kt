package com.example.chronomapscanner.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDate

@Entity(
    tableName = "background_variants",
    foreignKeys = [
        ForeignKey(
            entity = BackgroundCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(name = "idx_bg_variants_category", value = ["categoryId"])]
)
data class BackgroundVariantEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val imagePath: String?,
    val orderIndex: Int = 0,
    val dateAdded: LocalDate,
    val notes: String? = null
)
