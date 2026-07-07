package com.example.chronomapscanner.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chronomapscanner.data.repository.MoleRepository
import com.example.chronomapscanner.data.local.datastore.SettingsRepository
import com.example.chronomapscanner.ui.SplitViewUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory

@HiltViewModel(assistedFactory = SplitViewViewModel.Factory::class)
class SplitViewViewModel @AssistedInject constructor(
    @Assisted private val moleId: String,
    private val moleRepository: MoleRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(moleId: String): SplitViewViewModel
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val splitViewUiState: StateFlow<SplitViewUiState> = moleRepository.getMoleByIdWithHistory(moleId)
        .map { mole ->
            val photoHistory = mole?.history?.filter { it.imagePath != null }?.sortedBy { it.date } ?: emptyList()
            SplitViewUiState(photoHistory = photoHistory)
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SplitViewUiState()
        )
}
