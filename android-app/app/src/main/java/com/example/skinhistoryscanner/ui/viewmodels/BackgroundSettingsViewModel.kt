package com.example.skinhistoryscanner.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.domain.UserSettings
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import com.example.skinhistoryscanner.data.repository.BackgroundRepository
import com.example.skinhistoryscanner.data.repository.FileRepository
import com.example.skinhistoryscanner.data.local.room.BackgroundCategoryEntity
import com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity
import com.example.skinhistoryscanner.data.repository.MoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BackgroundSettingsViewModel @Inject constructor(
    private val backgroundRepository: BackgroundRepository,
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
    private val moleRepository: MoleRepository
) : ViewModel() {

    private val currentProfile = settingsRepository.currentProfile

    val userSettings = combine(
        settingsRepository.gender,
        settingsRepository.bodyType
    ) { gender, bodyType -> UserSettings(gender, bodyType) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings(Gender.MALE, BodyType.SLIM))

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categoriesFlow = currentProfile.flatMapLatest { profileName ->
        backgroundRepository.getCategoriesForProfile(profileName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCategoryId = settingsRepository.activeCategoryId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun switchCategory(newCategoryId: String, keepMoles: Boolean) {
        viewModelScope.launch {
            val oldCategoryId = activeCategoryId.value
            if (oldCategoryId != null && oldCategoryId != newCategoryId) {
                val oldVariants = backgroundRepository.getVariantsForCategorySync(oldCategoryId)
                if (keepMoles) {
                    val newVariants = backgroundRepository.getVariantsForCategorySync(newCategoryId)
                    val targetVariantId = newVariants.firstOrNull()?.id
                    if (targetVariantId != null) {
                        moleRepository.migrateMoles(oldVariants.map { it.id }, targetVariantId)
                    }
                } else {
                    oldVariants.forEach { moleRepository.deleteMolesByVariant(it.id) }
                }
            }
            settingsRepository.setActiveCategoryId(newCategoryId)
        }
    }

    fun updateBodySettings(gender: Gender, bodyType: BodyType) {
        viewModelScope.launch {
            settingsRepository.updateBodySettings(gender, bodyType)
        }
    }

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    fun selectCategoryForEditing(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val variantsForSelectedCategory = combine(
        _selectedCategoryId,
        activeCategoryId
    ) { selected, active ->
        selected ?: active
    }.flatMapLatest { catId ->
        if (catId != null) {
            backgroundRepository.getVariantsForCategory(catId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveTemporaryImageFromUri(uri: Uri, onSaved: (String?) -> Unit) {
        viewModelScope.launch {
            val path = fileRepository.saveImageFromUri(uri, "bg_tmp_")
            onSaved(path)
        }
    }

    fun addVariantFromPath(categoryId: String, name: String, imagePath: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val variants = variantsForSelectedCategory.first()
            val newOrderIndex = variants.size
            
            val newVariant = BackgroundVariantEntity(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                name = name,
                imagePath = imagePath,
                orderIndex = newOrderIndex,
                dateAdded = LocalDate.now(),
                notes = null
            )
            backgroundRepository.insertVariant(newVariant)
        }
    }

    fun addVariants(categoryId: String, uris: List<Uri>) {
        viewModelScope.launch {
            val variants = variantsForSelectedCategory.first()
            var nextOrderIndex = variants.size
            
            val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            uris.forEachIndexed { index, uri ->
                val imagePath = fileRepository.saveImageFromUri(uri, "bg_")
                val timeString = java.time.LocalTime.now().format(formatter)
                val newVariant = BackgroundVariantEntity(
                    id = UUID.randomUUID().toString(),
                    categoryId = categoryId,
                    name = "Variante $timeString ${index+1}",
                    imagePath = imagePath,
                    orderIndex = nextOrderIndex++,
                    dateAdded = LocalDate.now(),
                    notes = null
                )
                backgroundRepository.insertVariant(newVariant)
            }
        }
    }

    fun updateVariantName(variant: BackgroundVariantEntity, newName: String) {
        if (newName.isBlank() || variant.name == newName) return
        viewModelScope.launch {
            backgroundRepository.updateVariant(variant.copy(name = newName))
        }
    }

    fun updateVariantsOrder(variants: List<BackgroundVariantEntity>) {
        viewModelScope.launch {
            variants.forEachIndexed { index, variant ->
                backgroundRepository.updateVariant(variant.copy(orderIndex = index))
            }
        }
    }

    fun deleteVariant(variant: BackgroundVariantEntity) {
        viewModelScope.launch {
            backgroundRepository.deleteVariant(variant.id)
            // Cleanup the file is handled by FileCleanupWorker implicitly or we can delete it immediately.
            // OfflineMoleRepository also observes.
        }
    }
}
