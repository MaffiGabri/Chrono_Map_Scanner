package com.example.chronomapscanner.data.domain

import kotlinx.serialization.Serializable

@Serializable
data class AppDatabaseDto(
    val profileName: String,
    val profileImage: String?,
    val settings: UserSettings,
    val reminders: ReminderSettings,
    val colorSettings: List<ColorSetting> = emptyList(),
    val moles: List<Mole> = emptyList(),
    val categories: List<BackgroundCategoryDto> = emptyList(),
    val variants: List<BackgroundVariantDto> = emptyList()
)

@Serializable
data class BackgroundCategoryDto(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean
)

@Serializable
data class BackgroundVariantDto(
    val id: String,
    val categoryId: String,
    val name: String,
    val imagePath: String?,
    val orderIndex: Int,
    val dateAdded: String,
    val notes: String?
)
