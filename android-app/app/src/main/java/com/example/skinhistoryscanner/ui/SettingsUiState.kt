package com.example.skinhistoryscanner.ui

import com.example.skinhistoryscanner.data.domain.ReminderSettings
import com.example.skinhistoryscanner.data.domain.UserSettings
import androidx.compose.runtime.Immutable

/**
 * UI State for the Settings Screen.
 */
@Immutable
data class SettingsUiState(
    val profileName: String = "",
    val profileImage: String? = null,
    val profiles: List<String> = emptyList(),
    val userSettings: UserSettings = UserSettings(),
    val reminderSettings: ReminderSettings = ReminderSettings(),
    val moleCount: Int = 0,
    val keepLegendVisible: Boolean = false,
    val rapidInsertionMode: Boolean = false,
    val rapidUpdateMode: Boolean = false,
    val showZoomButton: Boolean = false,
    val scannerDelayMs: Long = 500L,
    val scannerIntervalMin: Long = 5L,
    val warnOnEmptyMoleDeletion: Boolean = true,
    val snapToRecentOnAddMole: Boolean = true,
    val isLoading: Boolean = false,
    val isGeneratingGlobalReport: Boolean = false
)
