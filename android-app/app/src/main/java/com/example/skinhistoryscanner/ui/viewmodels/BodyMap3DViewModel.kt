package com.example.skinhistoryscanner.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skinhistoryscanner.data.domain.Mole3D
import com.example.skinhistoryscanner.data.domain.Mole3DMapItem
import com.example.skinhistoryscanner.data.repository.Mole3DRepository
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class BodyMap3DUiState(
    val isLoading: Boolean = false,
    val models: List<String> = listOf("house.glb"),
    val activeModelIndex: Int = 0,
    val moles: List<Mole3DMapItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val availableDates: List<LocalDate> = listOf(LocalDate.now()),
    val profileName: String = ""
)

@HiltViewModel
class BodyMap3DViewModel @Inject constructor(
    private val mole3DRepository: Mole3DRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _activeModelIndex = MutableStateFlow(0)

    val uiState: StateFlow<BodyMap3DUiState> = combine(
        settingsRepository.currentProfile,
        _selectedDate,
        _activeModelIndex
    ) { profileName, date, modelIdx ->
        BodyMap3DUiState(
            profileName = profileName,
            selectedDate = date,
            activeModelIndex = modelIdx
        )
    }.flatMapLatest { state ->
        mole3DRepository.getMolesAtDate(state.profileName, state.selectedDate).map { moles ->
            state.copy(moles = moles)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BodyMap3DUiState())

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun setModelIndex(index: Int) {
        _activeModelIndex.value = index
    }

    fun addMole(x: Float, y: Float, z: Float, color: String) {
        viewModelScope.launch {
            val moleId = UUID.randomUUID().toString()
            val state = uiState.value
            val modelId = state.models[state.activeModelIndex]

            val mole = Mole3D(
                id = moleId,
                profileName = state.profileName,
                x = x,
                y = y,
                z = z,
                modelId = modelId,
                color = color
            )
            mole3DRepository.upsertMole(mole)
        }
    }
}
