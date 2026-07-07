package com.example.chronomapscanner.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "background_categories",
    indices = [
        Index(name = "idx_bg_categories_profile", value = ["profileName"])
    ]
)
data class BackgroundCategoryEntity(
    @PrimaryKey val id: String,
    val profileName: String,
    val name: String,
    val isBuiltIn: Boolean = false
)
