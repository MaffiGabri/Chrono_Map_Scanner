package com.example.skinhistoryscanner.ui.viewmodels

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skinhistoryscanner.data.domain.Mole as DomainMole
import com.example.skinhistoryscanner.data.domain.BodySide
import com.example.skinhistoryscanner.data.repository.MoleRepository
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.skinhistoryscanner.ui.BodyMapUiState
import com.example.skinhistoryscanner.data.domain.UserSettings
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.ColorSetting
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update
import androidx.compose.ui.graphics.asImageBitmap


import com.example.skinhistoryscanner.data.repository.BackgroundRepository
import com.example.skinhistoryscanner.ui.BackgroundVariantUiModel

@HiltViewModel
class BodyMapViewModel @Inject constructor(
    private val moleRepository: MoleRepository,
    private val settingsRepository: SettingsRepository,
    private val backgroundRepository: BackgroundRepository
) : ViewModel() {

    val currentProfile = settingsRepository.currentProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Default"
    )

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _currentVariantIndex = MutableStateFlow(0)
    
    private val thumbnailCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(150)
    private val loadingThumbnails = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val _cacheUpdateTrigger = MutableStateFlow(0)

    fun getThumbnail(path: String): androidx.compose.ui.graphics.ImageBitmap? {
        val cached = thumbnailCache.get(path)
        if (cached != null) return cached
        
        if (loadingThumbnails.add(path)) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        thumbnailCache.put(path, bitmap.asImageBitmap())
                        _cacheUpdateTrigger.update { it + 1 }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    loadingThumbnails.remove(path)
                }
            }
        }
        return null
    }
    
    init {
        viewModelScope.launch {
            currentProfile.collectLatest { profile ->
                backgroundRepository.ensureBuiltInCategoriesExist(profile)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val variantsFlow = settingsRepository.currentProfile.flatMapLatest { profile ->
        settingsRepository.activeCategoryId.flatMapLatest { activeCategoryId ->
            val flowToUse = if (activeCategoryId != null) {
                backgroundRepository.getVariantsForCategory(activeCategoryId)
            } else {
                // Seleziona la categoria predefinita (es. Persona)
                backgroundRepository.getCategoriesForProfile(profile).map { cats -> cats.firstOrNull()?.id }
                    .filterNotNull()
                    .flatMapLatest { backgroundRepository.getVariantsForCategory(it) }
            }
            flowToUse.map { list ->
                list.map { BackgroundVariantUiModel(it.id, it.name, it.imagePath, it.imagePath == null) }
            }
        }
    }.distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val availableDatesFlow = combine(
        settingsRepository.currentProfile,
        variantsFlow,
        _currentVariantIndex
    ) { profile, variants, index ->
        val activeVariantId = variants.getOrNull(index)?.id ?: ""
        profile to activeVariantId
    }.flatMapLatest { (profile, variantId) ->
        moleRepository.getAvailableDatesForVariant(profile, variantId).map { dates ->
            val parsed = dates.toSortedSet()
            if (parsed.isEmpty()) listOf(LocalDate.now()) else parsed.toList()
        }
    }.distinctUntilChanged()

    private val visibleColorsFlow = settingsRepository.colorSettings
        .map { list -> list.filter { it.visible }.map { it.hex } }
        .distinctUntilChanged()

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val sampledSelectedDate = _selectedDate.sample(50)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val currentMolesFlow = combine(
        settingsRepository.currentProfile,
        sampledSelectedDate
    ) { profile, date -> profile to date }
    .flatMapLatest { (profile, date) ->
        moleRepository.getMolesAtDate(profile, date)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    private val uiMolesAndCountsFlow = combine(
        currentMolesFlow,
        variantsFlow,
        _currentVariantIndex,
        visibleColorsFlow
    ) { moles, variants, index, visibleColors ->
        val activeVariantId = variants.getOrNull(index)?.id ?: ""
        val colorsToQuery = if (visibleColors.isEmpty()) listOf("NONE") else visibleColors
        
        val availableMolesForSide = moles.filter { it.side == activeVariantId }
        
        // Count all moles for this side before filtering by visibility
        val counts = availableMolesForSide.groupingBy { it.color }.eachCount()
        
        val filteredMoles = availableMolesForSide.filter { mole -> 
            colorsToQuery.contains(mole.color) 
        }.map { baseDto ->
            val hexColorString = baseDto.color
            val composeColor = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hexColorString)) } catch(e:Exception){ androidx.compose.ui.graphics.Color.Gray }
            
            val thumbPath = baseDto.historyDate?.let { _ ->
                baseDto.imagePath?.let { originalPath ->
                    val file = java.io.File(originalPath)
                    val thumbName = "thumb_${file.name}"
                    val parent = file.parent
                    if (parent != null) "$parent/$thumbName" else thumbName
                }
            }
            
            com.example.skinhistoryscanner.ui.MoleUiModel(
                id = baseDto.id,
                x = baseDto.x,
                y = baseDto.y,
                colorHex = hexColorString,
                color = composeColor,
                side = baseDto.side,
                latestPhotoPath = thumbPath
            )
        }
        
        filteredMoles to counts
    }.distinctUntilChanged()

    private val _userSettingsFlow = combine(
        settingsRepository.gender,
        settingsRepository.bodyType
    ) { gender, bodyType -> UserSettings(gender, bodyType) }

    private data class InterfaceSettingsData(val keepLegend: Boolean, val rapidInsert: Boolean, val rapidUpdate: Boolean, val showZoomButton: Boolean)
    private val _interfaceSettingsFlow = combine(
        settingsRepository.keepLegendVisible,
        settingsRepository.rapidInsertionMode,
        settingsRepository.rapidUpdateMode,
        settingsRepository.showZoomButton
    ) { keep, insert, update, zoom -> InterfaceSettingsData(keep, insert, update, zoom) }

    private data class UiTriggersData(val interfaceSettings: InterfaceSettingsData, val cacheTrigger: Int)
    private val _uiTriggersFlow = combine(_interfaceSettingsFlow, _cacheUpdateTrigger) { settings, trigger ->
        UiTriggersData(settings, trigger)
    }

    private data class ProfileData(val name: String, val image: String?)
    private val _profileFlow = combine(
        settingsRepository.currentProfile,
        settingsRepository.profileImage
    ) { name, image -> ProfileData(name, image) }

    private data class MapContextData(
        val variants: List<BackgroundVariantUiModel>,
        val currentVariantIndex: Int,
        val date: LocalDate,
        val profile: ProfileData,
        val colors: List<ColorSetting>,
        val isImporting: Boolean
    )

    private val _mapContextFlow = combine(
        combine(variantsFlow, _currentVariantIndex, sampledSelectedDate) { v, i, d -> Triple(v, i, d) },
        combine(_profileFlow, settingsRepository.colorSettings, settingsRepository.isImporting) { p, c, imp -> Triple(p, c, imp) }
    ) { (variants, currentVariantIndex, date), (profile, colors, isImporting) ->
        MapContextData(variants, currentVariantIndex, date, profile, colors, isImporting)
    }

    val bodyMapUiState: StateFlow<BodyMapUiState> = combine(
        uiMolesAndCountsFlow,
        _mapContextFlow,
        _userSettingsFlow,
        _uiTriggersFlow,
        availableDatesFlow
    ) { molesData, contextData, userSettings, uiTriggers, availableDates ->
        val (uiMoles, counts) = molesData
        BodyMapUiState(
            profileName = contextData.profile.name,
            profileImage = contextData.profile.image,
            variants = contextData.variants,
            currentVariant = contextData.variants.getOrNull(contextData.currentVariantIndex),
            selectedDate = contextData.date,
            colorSettings = contextData.colors,
            userSettings = userSettings,
            moles = uiMoles,
            moleCountsByColor = counts,
            keepLegendVisible = uiTriggers.interfaceSettings.keepLegend,
            rapidInsertionMode = uiTriggers.interfaceSettings.rapidInsert,
            rapidUpdateMode = uiTriggers.interfaceSettings.rapidUpdate,
            showZoomButton = uiTriggers.interfaceSettings.showZoomButton,
            availableDates = availableDates,
            cacheTrigger = uiTriggers.cacheTrigger,
            isLoading = contextData.isImporting
        )
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BodyMapUiState(isLoading = true)
    )

    fun cycleVariant() {
        val variants = bodyMapUiState.value.variants
        if (variants.isNotEmpty()) {
            _currentVariantIndex.value = (_currentVariantIndex.value + 1) % variants.size
        }
    }

    fun setVariantIndex(index: Int) {
        _currentVariantIndex.value = index
    }

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }


    fun addMole(x: Float, y: Float, variantId: String, color: String): String {
        val moleId = UUID.randomUUID().toString()
        
        viewModelScope.launch {
            val profileName = settingsRepository.currentProfile.first()
            val mole = DomainMole(
                id = moleId,
                profileName = profileName,
                x = x,
                y = y,
                side = variantId,
                color = color
            )
            moleRepository.upsertMole(mole)
            
            val snap = settingsRepository.snapToRecentOnAddMole.first()
            if (snap) {
                setSelectedDate(LocalDate.now())
            }
        }
        return moleId
    }

    fun updateMolePosition(moleId: String, newX: Float, newY: Float, variantId: String) {
        viewModelScope.launch {
            moleRepository.updateMolePosition(moleId, newX, newY, variantId)
        }
    }

    fun updateMoleColor(moleId: String, newColor: String) {
        viewModelScope.launch {
            moleRepository.updateMoleColor(moleId, newColor)
        }
    }
    
    fun toggleColorVisibility(hex: String) {
        viewModelScope.launch {
            settingsRepository.toggleColorVisibility(hex)
        }
    }

    suspend fun snapMolePosition(xPct: Float, yPct: Float, canvasWidth: Float, canvasHeight: Float, radiusPx: Float): Pair<Float, Float> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val uiMoles = bodyMapUiState.value.moles
        val minCenterDistancePx = radiusPx * 3f // Un neo di distanza in mezzo, più i due mezzi raggi = 3 raggio totale tra i centri
        val minDistanceSq = minCenterDistancePx * minCenterDistancePx
        
        var currXPx = (xPct / 100f) * canvasWidth
        var currYPx = (yPct / 100f) * canvasHeight
        
        var attempts = 0
        var collisionFound = true
        while (collisionFound && attempts < 10) {
            collisionFound = false
            var closestX = 0f
            var closestY = 0f
            var minColDistSq = Float.MAX_VALUE
            
            for (i in uiMoles.indices) {
                val mole = uiMoles[i]
                val mxPx = (mole.x / 100f) * canvasWidth
                val myPx = (mole.y / 100f) * canvasHeight
                val dx = currXPx - mxPx
                val dy = currYPx - myPx
                val distSq = dx*dx + dy*dy
                if (distSq < minDistanceSq && distSq < minColDistSq) {
                    minColDistSq = distSq
                    closestX = mxPx
                    closestY = myPx
                    collisionFound = true
                }
            }
            
            if (collisionFound) {
                val dx = currXPx - closestX
                val dy = currYPx - closestY
                val dist = kotlin.math.sqrt(dx*dx + dy*dy.toDouble()).toFloat()
                
                var dirX = 1f
                var dirY = 0f
                if (dist >= 0.001f) {
                    dirX = dx / dist
                    dirY = dy / dist
                }
                
                currXPx = closestX + dirX * minCenterDistancePx
                currYPx = closestY + dirY * minCenterDistancePx
            }
            attempts++
        }
        
        Pair(((currXPx / canvasWidth) * 100f).coerceIn(0f, 100f), ((currYPx / canvasHeight) * 100f).coerceIn(0f, 100f))
    }

    private var cachedGrid: Map<Int, List<Pair<Int, com.example.skinhistoryscanner.ui.MoleUiModel>>>? = null
    private var lastMolesRef: List<com.example.skinhistoryscanner.ui.MoleUiModel>? = null
    private val gridMutex = Mutex()

    suspend fun findMoleAtTap(internalX: Float, internalY: Float, canvasWidth: Float, canvasHeight: Float, thresholdSq: Float): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val uiMoles = bodyMapUiState.value.moles
        
        // Spatial Hashing: Build grid if needed
        val grid = gridMutex.withLock {
            if (cachedGrid == null || lastMolesRef !== uiMoles) {
                val newGrid = mutableMapOf<Int, MutableList<Pair<Int, com.example.skinhistoryscanner.ui.MoleUiModel>>>()
                val GRID_DIVISIONS = 10
                for ((index, mole) in uiMoles.withIndex()) {
                    val bucketX = (mole.x / 100f * GRID_DIVISIONS).toInt().coerceIn(0, GRID_DIVISIONS - 1)
                    val bucketY = (mole.y / 100f * GRID_DIVISIONS).toInt().coerceIn(0, GRID_DIVISIONS - 1)
                    val bucketId = bucketY * GRID_DIVISIONS + bucketX
                    newGrid.getOrPut(bucketId) { mutableListOf() }.add(Pair(index, mole))
                }
                cachedGrid = newGrid
                lastMolesRef = uiMoles
            }
            cachedGrid!!
        }
        val GRID_DIVISIONS = 10
        
        // Convert tap to relative coordinates to find the central bucket
        val tapRelX = (internalX - canvasWidth / 2f) / canvasWidth + 0.5f
        val tapRelY = (internalY - canvasHeight / 2f) / canvasHeight + 0.5f
        
        val tapBucketX = (tapRelX * GRID_DIVISIONS).toInt().coerceIn(0, GRID_DIVISIONS - 1)
        val tapBucketY = (tapRelY * GRID_DIVISIONS).toInt().coerceIn(0, GRID_DIVISIONS - 1)
        
        var minDistance = Float.MAX_VALUE
        var clickedMoleId: String? = null
        
        // Query the 3x3 grid neighborhood to cover boundary cases (large moles overlapping cells)
        for (dxBucket in -1..1) {
            for (dyBucket in -1..1) {
                val bx = tapBucketX + dxBucket
                val by = tapBucketY + dyBucket
                if (bx in 0 until GRID_DIVISIONS && by in 0 until GRID_DIVISIONS) {
                    val bucketId = by * GRID_DIVISIONS + bx
                    val molesInBucket = grid[bucketId] ?: continue
                    
                    for ((_, mole) in molesInBucket) {
                        val relX = mole.x / 100f
                        val relY = mole.y / 100f
                        val posX = (relX - 0.5f) * canvasWidth + (canvasWidth / 2f)
                        val posY = (relY - 0.5f) * canvasHeight + (canvasHeight / 2f)
                        
                        val dx = internalX - posX
                        val dy = internalY - posY
                        val distSq = dx * dx + dy * dy
                        
                        // We pick the mole whose Euclidean center is strictly closest to the tap
                        if (distSq < minDistance) {
                            minDistance = distSq
                            clickedMoleId = mole.id
                        }
                    }
                }
            }
        }
        
        if (minDistance <= thresholdSq) clickedMoleId else null
    }
}
