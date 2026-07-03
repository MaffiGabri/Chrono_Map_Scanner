package com.example.chronomapscanner.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.chronomapscanner.data.local.room.AppDatabaseRoom
import androidx.lifecycle.viewModelScope
import com.example.chronomapscanner.data.domain.BodyType
import com.example.chronomapscanner.data.domain.Gender
import com.example.chronomapscanner.data.domain.ReminderUnit
import com.example.chronomapscanner.data.repository.BackupRepository
import com.example.chronomapscanner.data.repository.FileRepository
import com.example.chronomapscanner.data.repository.MoleRepository
import com.example.chronomapscanner.data.local.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.chronomapscanner.data.domain.AppDatabaseDto
import com.example.chronomapscanner.data.domain.ReminderSettings
import com.example.chronomapscanner.data.domain.UserSettings
import com.example.chronomapscanner.data.domain.Mole as DomainMole
import com.example.chronomapscanner.data.domain.HistoryEntry as DomainHistoryEntry
import com.example.chronomapscanner.ui.SettingsUiState
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import com.example.chronomapscanner.di.ApplicationScope
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.chronomapscanner.data.local.workers.ExportDatabaseWorker
import com.example.chronomapscanner.data.local.workers.ImportDatabaseWorker

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val moleRepository: MoleRepository,
    private val backupRepository: BackupRepository,
    private val fileRepository: FileRepository,
    private val backgroundRepository: com.example.chronomapscanner.data.repository.BackgroundRepository,
    private val database: AppDatabaseRoom,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val currentProfile = settingsRepository.currentProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Default"
    )

    val allProfiles: StateFlow<List<String>> = combine(
        moleRepository.getAllProfileNames(),
        settingsRepository.currentProfile
    ) { existingProfiles, current ->
        (existingProfiles + current).distinct().filter { it.isNotBlank() }.sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("Default")
    )



    private val _userSettingsFlow = combine(
        settingsRepository.gender,
        settingsRepository.bodyType
    ) { gender, bodyType -> UserSettings(gender, bodyType) }

    private val _reminderSettingsFlow = combine(
        settingsRepository.remindersEnabled,
        settingsRepository.remindersValue,
        settingsRepository.remindersUnit,
        settingsRepository.lastReminderDate
    ) { enabled, value, unit, lastDate -> ReminderSettings(enabled, value, unit, lastDate) }

    private data class SettingsStatePart1(
        val profileName: String,
        val profileImage: String?,
        val profiles: List<String>,
        val moleCount: Int
    )

    // Rapid modes flow
    private val _rapidModesFlow = combine(
        settingsRepository.keepLegendVisible,
        settingsRepository.rapidInsertionMode,
        settingsRepository.rapidUpdateMode,
        settingsRepository.snapToRecentOnAddMole
    ) { keep, insert, update, snapToRecent -> object {
        val keep = keep
        val insert = insert
        val update = update
        val snapToRecent = snapToRecent
    } }

    private val _interfaceSettingsFlow = combine(
        _rapidModesFlow,
        settingsRepository.showZoomButton,
        settingsRepository.scannerDelayMs,
        settingsRepository.scannerIntervalMin,
        settingsRepository.warnOnEmptyMoleDeletion
    ) { rapidModes, showZoom, delayMs, intervalMin, warnEmpty ->
        object {
            val keepLegend = rapidModes.keep
            val rapidInsert = rapidModes.insert
            val rapidUpdate = rapidModes.update
            val snapToRecent = rapidModes.snapToRecent
            val showZoomButton = showZoom
            val scannerDelayMs = delayMs
            val scannerIntervalMin = intervalMin
            val warnOnEmptyMoleDeletion = warnEmpty
        }
    }

    // Aggregate settings state
    val settingsUiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.currentProfile,
            settingsRepository.profileImage,
            allProfiles,
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            settingsRepository.currentProfile.flatMapLatest { moleRepository.getMolesCountForProfile(it) },
            ::SettingsStatePart1
        ),
        _userSettingsFlow,
        _reminderSettingsFlow,
        _interfaceSettingsFlow
    ) { part1, userSettings, reminderSettings, interfaceSettings ->
        SettingsUiState(
            profileName = part1.profileName,
            profileImage = part1.profileImage,
            profiles = part1.profiles,
            userSettings = userSettings,
            reminderSettings = reminderSettings,
            moleCount = part1.moleCount,
            keepLegendVisible = interfaceSettings.keepLegend,
            rapidInsertionMode = interfaceSettings.rapidInsert,
            rapidUpdateMode = interfaceSettings.rapidUpdate,
            showZoomButton = interfaceSettings.showZoomButton,
            scannerDelayMs = interfaceSettings.scannerDelayMs,
            scannerIntervalMin = interfaceSettings.scannerIntervalMin,
            warnOnEmptyMoleDeletion = interfaceSettings.warnOnEmptyMoleDeletion,
            snapToRecentOnAddMole = interfaceSettings.snapToRecent
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _pendingImportUri = MutableStateFlow<Uri?>(null)
    val pendingImportUri = _pendingImportUri.asStateFlow()

    fun setPendingImportUri(uri: Uri?) {
        _pendingImportUri.value = uri
    }


    fun switchProfile(name: String) {
        viewModelScope.launch {
            settingsRepository.setCurrentProfile(name)
        }
    }

    fun addProfile(name: String) {
        if (name.isBlank() || allProfiles.value.contains(name)) return
        viewModelScope.launch {
            settingsRepository.setCurrentProfile(name)
        }
    }

    fun deleteProfile(name: String) {
        if (allProfiles.value.size <= 1) return
        viewModelScope.launch {
            if (name == currentProfile.value) {
                settingsRepository.setProfileImage(null) // Clear profile image
                switchProfile(allProfiles.value.filter { it != name }.firstOrNull() ?: "Default")
            }
            
            applicationScope.launch(kotlinx.coroutines.Dispatchers.IO + NonCancellable) {
                moleRepository.deleteMolesByProfile(name)
            }
        }
    }

    fun renameProfile(oldName: String, newName: String) {
        if (newName.isBlank() || allProfiles.value.contains(newName)) return
        viewModelScope.launch {
            moleRepository.renameProfile(oldName, newName)
            settingsRepository.renameProfileSettings(oldName, newName)
            if (oldName == currentProfile.value) {
                settingsRepository.setCurrentProfile(newName)
            }
        }
    }

    fun updateProfileInfo(oldName: String, newName: String, imagePath: String?) {
        viewModelScope.launch {
            if (newName != oldName) {
                moleRepository.renameProfile(oldName, newName)
                settingsRepository.renameProfileSettings(oldName, newName)
                // Poiché updateProfileInfo viene chiamato SOLO per il profilo attualmente attivo,
                // aggiorniamo direttamente il CurrentProfile senza condizioni che potrebbero fallire.
                settingsRepository.setCurrentProfile(newName)
            }
            if (imagePath != null) {
                settingsRepository.setProfileImage(imagePath, newName)
            }
        }
    }

    fun updateBodySettings(gender: Gender, bodyType: BodyType) {
        viewModelScope.launch {
            settingsRepository.updateBodySettings(gender, bodyType)
        }
    }

    fun updateReminderSettings(enabled: Boolean, value: Int, unit: ReminderUnit) {
        viewModelScope.launch {
            val currentState = settingsUiState.value.reminderSettings
            settingsRepository.updateReminderSettings(
                enabled = enabled,
                value = value,
                unit = unit,
                lastDate = currentState.lastReminderDate
            )
            com.example.chronomapscanner.notifications.ReminderManager.scheduleReminder(
                context, enabled, value, unit
            )
        }
    }

    fun saveImageFromUri(uri: Uri, prefix: String, onSaved: (String?) -> Unit) {
        viewModelScope.launch {
            val path = fileRepository.saveImageFromUri(uri, prefix)
            onSaved(path)
        }
    }

    enum class ImportMode { OVERWRITE, NEW_PROFILE }

    fun importDatabase(importUri: Uri, mode: ImportMode, newName: String? = null, onComplete: () -> Unit) {
        _isProcessing.value = true
        val inputData = Data.Builder()
            .putString("import_uri", importUri.toString())
            .putString("mode", mode.name)
            .putString("new_name", newName)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<ImportDatabaseWorker>()
            .setInputData(inputData)
            .build()
            
        WorkManager.getInstance(context).enqueue(workRequest)
        // In a real app we would observe the WorkInfo, but for now we'll just simulate completion
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _isProcessing.value = false
            onComplete()
        }
    }

    fun exportDatabase(profileName: String = currentProfile.value, destinationUri: Uri, onComplete: () -> Unit) {
        _isProcessing.value = true
        val inputData = Data.Builder()
            .putString("profile_name", profileName)
            .putString("destination_uri", destinationUri.toString())
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<ExportDatabaseWorker>()
            .setInputData(inputData)
            .build()
            
        WorkManager.getInstance(context).enqueue(workRequest)
        // Simulate completion
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _isProcessing.value = false
            onComplete()
        }
    }

    fun updateRapidModes(keepLegend: Boolean, rapidInsert: Boolean, rapidUpdate: Boolean, snapToRecent: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateRapidModes(keepLegend, rapidInsert, rapidUpdate)
            settingsRepository.setSnapToRecentOnAddMole(snapToRecent)
        }
    }

    fun updateShowZoomButton(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateShowZoomButton(show)
        }
    }

    fun debugResetAndSeed() {
        viewModelScope.launch {
            _isProcessing.value = true
            val activeProfile = settingsRepository.currentProfile.first()
            backgroundRepository.ensureBuiltInCategoriesExist(activeProfile)
            com.example.chronomapscanner.utils.Seeder.seedFakeData(
                context, 
                database, 
                activeProfile,
                forceReset = true
            )
            _isProcessing.value = false
        }
    }

    fun testNotification() {
        val testRequest = OneTimeWorkRequestBuilder<com.example.chronomapscanner.notifications.ReminderWorker>().build()
        WorkManager.getInstance(context).enqueue(testRequest)
    }

    fun updateScannerSettings(delayMs: Long, intervalMin: Long) {
        viewModelScope.launch {
            settingsRepository.setScannerDelayMs(delayMs)
            settingsRepository.setScannerIntervalMin(intervalMin)
        }
    }

    fun updateWarnOnEmptyMoleDeletion(warn: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWarnOnEmptyMoleDeletion(warn)
        }
    }
}
