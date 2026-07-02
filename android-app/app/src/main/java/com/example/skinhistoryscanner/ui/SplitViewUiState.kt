package com.example.skinhistoryscanner.ui

import com.example.skinhistoryscanner.data.domain.HistoryEntry
import androidx.compose.runtime.Immutable

/**
 * UI State for the Split View Screen.
 */
@Immutable
data class SplitViewUiState(
    val photoHistory: List<HistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
