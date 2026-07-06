package com.example.skinhistoryscanner.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.repository.FileRepository
import com.example.skinhistoryscanner.data.repository.MoleRepository
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import com.example.skinhistoryscanner.data.domain.UserSettings
import com.example.skinhistoryscanner.ui.MoleDetailsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.LocalDate
import javax.inject.Inject

import androidx.navigation.toRoute
import com.example.skinhistoryscanner.ui.navigation.MoleDetailsRoute

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory

@HiltViewModel(assistedFactory = MoleDetailsViewModel.Factory::class)
class MoleDetailsViewModel @AssistedInject constructor(
    @Assisted private val moleId: String,
    private val moleRepository: MoleRepository,
    private val backgroundRepository: com.example.skinhistoryscanner.data.repository.BackgroundRepository,
    private val fileRepository: com.example.skinhistoryscanner.data.repository.FileRepository,
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
                flowOf(null as com.example.skinhistoryscanner.data.domain.Mole? to null as com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity?)
            }
        },
        settingsRepository.colorSettings,
        settingsRepository.userSettings,
        _isGeneratingReport
    ) { moleAndVariant, colors, settings, isGen ->
        MoleDetailsUiState(
            mole = moleAndVariant.first as? com.example.skinhistoryscanner.data.domain.Mole,
            variant = moleAndVariant.second as? com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity,
            colorSettings = colors,
            userSettings = settings,
            isGeneratingReport = isGen
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

    fun generateMoleReport(context: android.content.Context, getColorLabel: (String) -> String, onComplete: (java.io.File) -> Unit) {
        if (_isGeneratingReport.value) return
        _isGeneratingReport.value = true
        val currentMole = moleDetailsUiState.value.mole
        if (currentMole == null) {
            _isGeneratingReport.value = false
            return
        }
        val userSettings = moleDetailsUiState.value.userSettings
        val colorLabel = getColorLabel(currentMole.color)

        viewModelScope.launch {
            try {
                val file = com.example.skinhistoryscanner.utils.GlobalReportGenerator.generateMolePdf(
                    context = context,
                    mole = currentMole,
                    userSettings = userSettings,
                    colorLabel = colorLabel
                )
                onComplete(file)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isGeneratingReport.value = false
            }
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
}
