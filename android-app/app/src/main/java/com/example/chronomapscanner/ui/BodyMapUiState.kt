package com.example.chronomapscanner.ui

import com.example.chronomapscanner.data.domain.ColorSetting
import com.example.chronomapscanner.ui.MoleUiModel
import com.example.chronomapscanner.data.domain.UserSettings
import java.time.LocalDate
import androidx.compose.runtime.Immutable

/**
 * UI State for the Body Map Screen.
 * Contains only the data needed by the UI, pre-filtered and formatted.
 */
data class BackgroundVariantUiModel(
    val id: String,
    val name: String,
    val imagePath: String?,
    val isBuiltIn: Boolean
)

@Immutable
data class BodyMapUiState(
    val profileName: String = "",
    val profileImage: String? = null,
    val variants: List<BackgroundVariantUiModel> = emptyList(),
    val currentVariant: BackgroundVariantUiModel? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val moles: List<MoleUiModel> = emptyList(),
    val colorSettings: List<ColorSetting> = emptyList(),
    val userSettings: UserSettings = UserSettings(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val moleCountsByColor: Map<String, Int> = emptyMap(),
    val keepLegendVisible: Boolean = false,
    val rapidInsertionMode: Boolean = false,
    val rapidUpdateMode: Boolean = false,
    val showZoomButton: Boolean = false,
    val availableDates: List<LocalDate> = emptyList(),
    val cacheTrigger: Int = 0
)
