package com.example.skinhistoryscanner.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import com.example.skinhistoryscanner.data.repository.BackgroundRepository
import com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class VariantManagementViewModel @Inject constructor(
    private val backgroundRepository: BackgroundRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val variants = settingsRepository.activeCategoryId.flatMapLatest { categoryId ->
        if (categoryId != null) {
            backgroundRepository.getVariantsForCategory(categoryId)
        } else {
            settingsRepository.currentProfile.flatMapLatest { profile ->
                backgroundRepository.getCategoriesForProfile(profile)
                    .map { cats -> cats.firstOrNull()?.id }
                    .filterNotNull()
                    .flatMapLatest { backgroundRepository.getVariantsForCategory(it) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateVariantOrder(fromIndex: Int, toIndex: Int) {
        val currentList = variants.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            
            // Aggiorna orderIndex per tutte le varianti coinvolte
            viewModelScope.launch {
                currentList.forEachIndexed { index, variant ->
                    if (variant.orderIndex != index) {
                        backgroundRepository.updateVariant(variant.copy(orderIndex = index))
                    }
                }
            }
        }
    }

    fun updateVariantDate(variantId: String, newDate: LocalDate) {
        viewModelScope.launch {
            val variant = variants.value.find { it.id == variantId }
            if (variant != null) {
                backgroundRepository.updateVariant(variant.copy(dateAdded = newDate))
            }
        }
    }

    fun updateVariantNotes(variantId: String, newNotes: String) {
        viewModelScope.launch {
            val variant = variants.value.find { it.id == variantId }
            if (variant != null) {
                backgroundRepository.updateVariant(variant.copy(notes = newNotes.ifBlank { null }))
            }
        }
    }
}
