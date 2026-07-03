package com.example.skinhistoryscanner.ui.viewmodels

import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.ColorSetting
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.domain.ReminderUnit
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSettingsRepository {
    val currentProfile = MutableStateFlow("Default")
    val profileImage = MutableStateFlow<String?>(null)
    val gender = MutableStateFlow(Gender.MALE)
    val bodyType = MutableStateFlow(BodyType.SLIM)
    val remindersEnabled = MutableStateFlow(false)
    val remindersValue = MutableStateFlow(1)
    val remindersUnit = MutableStateFlow(ReminderUnit.MONTHS)
    val lastReminderDate = MutableStateFlow<String?>(null)
    val keepLegendVisible = MutableStateFlow(false)
    val rapidInsertionMode = MutableStateFlow(false)
    val rapidUpdateMode = MutableStateFlow(false)
    val scannerDelayMs = MutableStateFlow(500L)
    val scannerIntervalMin = MutableStateFlow(5L)
    val showZoomButton = MutableStateFlow(false)
    val warnOnEmptyMoleDeletion = MutableStateFlow(true)
    val snapToRecentOnAddMole = MutableStateFlow(true)
    val isImporting = MutableStateFlow(false)

    private val _colorSettings = MutableStateFlow(
        listOf(
            ColorSetting("#ef4444", "Allarme / Controllo urgente", true),
            ColorSetting("#f97316", "Sospetto / In evoluzione", true)
        )
    )
    val colorSettings: Flow<List<ColorSetting>> = _colorSettings

    suspend fun setCurrentProfile(profile: String) { currentProfile.value = profile }
    suspend fun setProfileImage(path: String?) { profileImage.value = path }
    suspend fun updateBodySettings(g: Gender, b: BodyType) { gender.value = g; bodyType.value = b }
    suspend fun updateReminderSettings(e: Boolean, v: Int, u: ReminderUnit, l: String? = null) {
        remindersEnabled.value = e; remindersValue.value = v; remindersUnit.value = u
        if (l != null) lastReminderDate.value = l
    }
    suspend fun toggleColorVisibility(hex: String) {
        _colorSettings.value = _colorSettings.value.map {
            if (it.hex == hex) it.copy(visible = !it.visible) else it
        }
    }
    suspend fun updateRapidModes(keep: Boolean, insert: Boolean, update: Boolean, snap: Boolean) {
        keepLegendVisible.value = keep
        rapidInsertionMode.value = insert
        rapidUpdateMode.value = update
        snapToRecentOnAddMole.value = snap
    }
    suspend fun setScannerDelayMs(delay: Long) { scannerDelayMs.value = delay }
    suspend fun setScannerIntervalMin(interval: Long) { scannerIntervalMin.value = interval }
    suspend fun updateShowZoomButton(show: Boolean) { showZoomButton.value = show }
    suspend fun setWarnOnEmptyMoleDeletion(warn: Boolean) { warnOnEmptyMoleDeletion.value = warn }
    suspend fun setImportingState(importing: Boolean) { isImporting.value = importing }
}
