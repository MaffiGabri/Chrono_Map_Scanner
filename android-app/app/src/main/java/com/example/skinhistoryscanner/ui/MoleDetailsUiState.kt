package com.example.skinhistoryscanner.ui

import com.example.skinhistoryscanner.data.domain.ColorSetting
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.domain.UserSettings
import com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity
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
    val errorMessage: String? = null
)
