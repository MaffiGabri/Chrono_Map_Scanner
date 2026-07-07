package com.example.chronomapscanner.ui

import com.example.chronomapscanner.data.domain.ColorSetting
import com.example.chronomapscanner.data.domain.Mole
import com.example.chronomapscanner.data.domain.UserSettings
import com.example.chronomapscanner.data.local.room.BackgroundVariantEntity
import androidx.compose.runtime.Immutable

/**
 * UI State for the Mole Details Screen.
 */
@Immutable
data class MoleDetailsUiState(
    val mole: Mole? = null,
    val variant: BackgroundVariantEntity? = null,
    val colorSettings: List<ColorSetting> = emptyList(),
    val userSettings: UserSettings = UserSettings(),
    val isLoading: Boolean = false,
    val isGeneratingReport: Boolean = false,
    val errorMessage: String? = null
)
