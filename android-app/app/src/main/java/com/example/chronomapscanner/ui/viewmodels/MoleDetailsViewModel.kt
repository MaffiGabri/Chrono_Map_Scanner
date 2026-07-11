package com.example.chronomapscanner.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chronomapscanner.data.domain.HistoryEntry
import com.example.chronomapscanner.data.domain.Mole
import com.example.chronomapscanner.data.repository.FileRepository
import com.example.chronomapscanner.data.repository.MoleRepository
import com.example.chronomapscanner.data.local.datastore.SettingsRepository
import com.example.chronomapscanner.data.domain.UserSettings
import com.example.chronomapscanner.ui.MoleDetailsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.LocalDate
import javax.inject.Inject

import androidx.navigation.toRoute
import com.example.chronomapscanner.ui.navigation.MoleDetailsRoute

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory

@HiltViewModel(assistedFactory = MoleDetailsViewModel.Factory::class)
class MoleDetailsViewModel @AssistedInject constructor(
    @Assisted private val moleId: String,
    private val moleRepository: MoleRepository,
    private val backgroundRepository: com.example.chronomapscanner.data.repository.BackgroundRepository,
    private val fileRepository: com.example.chronomapscanner.data.repository.FileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(moleId: String): MoleDetailsViewModel
    }

    private val _isGeneratingReport = kotlinx.coroutines.flow.MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val moleDetailsUiState: StateFlow<MoleDetailsUiState> = combine(
        moleRepository.getMoleByIdWithHistory(moleId).flatMapLatest { mole ->
            if (mole != null) {
                backgroundRepository.getVariantByIdFlow(mole.side).map { variant ->
                    mole to variant
                }
            } else {
                flowOf(null as com.example.chronomapscanner.data.domain.Mole? to null as com.example.chronomapscanner.data.local.room.BackgroundVariantEntity?)
            }
        },
        settingsRepository.colorSettings,
        settingsRepository.gender,
        settingsRepository.bodyType,
        _isGeneratingReport
    ) { moleAndVariant, colors, gender, bodyType, isGenerating ->
        MoleDetailsUiState(
            mole = moleAndVariant.first,
            variant = moleAndVariant.second,
            colorSettings = colors,
            userSettings = UserSettings(gender, bodyType),
            isGeneratingReport = isGenerating
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MoleDetailsUiState()
    )


    val warnOnEmptyMoleDeletion: StateFlow<Boolean> = settingsRepository.warnOnEmptyMoleDeletion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setWarnOnEmptyMoleDeletion(warn: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWarnOnEmptyMoleDeletion(warn)
        }
    }

    fun deleteMole() {
        val currentMole = moleDetailsUiState.value.mole ?: return
        
        viewModelScope.launch {
            moleRepository.deleteMole(currentMole.id)
        }
    }

    fun updateMoleColor(color: String) {
        val currentMole = moleDetailsUiState.value.mole ?: return
        viewModelScope.launch {
            moleRepository.upsertMole(
                Mole(
                    id = currentMole.id,
                    profileName = currentMole.profileName,
                    x = currentMole.x,
                    y = currentMole.y,
                    side = currentMole.side,
                    color = color
                )
            )
        }
    }

    fun addHistoryEntry(date: LocalDate, imagePath: String? = null, notes: String? = null) {
        val id = moleDetailsUiState.value.mole?.id ?: return
        viewModelScope.launch {
            moleRepository.upsertHistoryEntry(
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    moleId = id,
                    date = date,
                    imagePath = imagePath,
                    notes = notes
                )
            )
        }
    }

    fun updateHistoryEntry(entryId: String, date: LocalDate, notes: String? = null, imagePath: String? = null) {
        val currentMole = moleDetailsUiState.value.mole ?: return
        val currentEntry = currentMole.history.find { it.id == entryId } ?: return
        var oldImagePath: String? = null
        
        if (imagePath != null && currentEntry.imagePath != null && currentEntry.imagePath != imagePath) {
            oldImagePath = currentEntry.imagePath
        }

        viewModelScope.launch {
            moleRepository.upsertHistoryEntry(
                HistoryEntry(
                    id = entryId,
                    moleId = currentMole.id,
                    date = date,
                    imagePath = imagePath ?: currentEntry.imagePath,
                    notes = notes
                )
            )
            if (oldImagePath != null) {
                fileRepository.scheduleFileDeletion(listOf(oldImagePath))
            }
        }
    }

    fun deleteHistoryEntry(entryId: String) {
        viewModelScope.launch {
            moleRepository.deleteHistoryEntry(entryId)
        }
    }

    fun saveImageFromUri(uri: Uri, prefix: String, onSaved: (String?) -> Unit) {
        viewModelScope.launch {
            val path = fileRepository.saveImageFromUri(uri, prefix)
            onSaved(path)
        }
    }

    fun discardDraftIfEmpty() {
        val currentMole = moleDetailsUiState.value.mole ?: return
        val isEmpty = currentMole.history.isEmpty() || currentMole.history.all { it.imagePath == null && it.notes.isNullOrBlank() }
        if (isEmpty) {
            viewModelScope.launch {
                moleRepository.deleteMole(currentMole.id)
            }
        }
    }

    fun generateMoleReport(context: android.content.Context, onComplete: (java.io.File) -> Unit) {
        if (_isGeneratingReport.value) return
        _isGeneratingReport.value = true
        val currentMole = moleDetailsUiState.value.mole
        if (currentMole == null) {
            _isGeneratingReport.value = false
            return
        }
        val userSettings = moleDetailsUiState.value.userSettings

        viewModelScope.launch {
            try {
                val colors = settingsRepository.colorSettings.first()
                val colorLabel = colors.find { it.hex.equals(currentMole.color.removePrefix("#"), ignoreCase = true) }?.label ?: context.getString(com.example.chronomapscanner.R.string.color_other)
                val variantName = moleDetailsUiState.value.variant?.name
                val file = com.example.chronomapscanner.utils.GlobalReportGenerator.generateMolePdf(
                    context = context,
                    mole = currentMole,
                    userSettings = userSettings,
                    colorLabel = colorLabel,
                    variantName = variantName
                )
                onComplete(file)
            } catch (e: Exception) {
                // Ignore per ora
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }
}
