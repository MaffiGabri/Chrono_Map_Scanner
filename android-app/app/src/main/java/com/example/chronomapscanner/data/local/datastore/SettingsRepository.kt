package com.example.chronomapscanner.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.chronomapscanner.data.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private val defaultColorSettings = listOf(
        ColorSetting("#ef4444", "color_alarm"),
        ColorSetting("#f97316", "color_suspicious"),
        ColorSetting("#eab308", "color_monitor"),
        ColorSetting("#22c55e", "color_safe"),
        ColorSetting("#3b82f6", "color_new"),
        ColorSetting("#a855f7", "color_other")
    )

    private object PreferencesKeys {
        val CURRENT_PROFILE = stringPreferencesKey("current_profile")
        fun profileImageKey(profile: String) = stringPreferencesKey("profile_image_$profile")
        fun genderKey(profile: String) = stringPreferencesKey("gender_$profile")
        fun bodyTypeKey(profile: String) = stringPreferencesKey("body_type_$profile")
        fun activeCategoryIdKey(profile: String) = stringPreferencesKey("active_category_id_$profile")
        
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDERS_VALUE = intPreferencesKey("reminders_value")
        val REMINDERS_UNIT = stringPreferencesKey("reminders_unit")
        val LAST_REMINDER_DATE = stringPreferencesKey("last_reminder_date")
        val COLOR_VISIBILITY_PREFIX = "color_visible_"

        val KEEP_LEGEND_VISIBLE = booleanPreferencesKey("keep_legend_visible")
        val RAPID_INSERTION = booleanPreferencesKey("rapid_insertion")
        val RAPID_UPDATE = booleanPreferencesKey("rapid_update")
        val SHOW_ZOOM_BUTTON = booleanPreferencesKey("show_zoom_button")
        val SNAP_TO_RECENT_ON_ADD_MOLE = booleanPreferencesKey("snap_to_recent_on_add_mole")

        val SCANNER_DELAY_MS = longPreferencesKey("scanner_delay_ms")
        val SCANNER_INTERVAL_MIN = longPreferencesKey("scanner_interval_min")
        
        val WARN_ON_EMPTY_MOLE_DELETION = booleanPreferencesKey("warn_on_empty_mole_deletion")
        val PDF_QUALITY = stringPreferencesKey("pdf_quality")
        val OPEN_PDF_AUTOMATICALLY = booleanPreferencesKey("open_pdf_automatically")
        val SHOW_EXPORT_DIALOG = booleanPreferencesKey("show_export_dialog")
    }

    /**
     * Profile Settings
     */
    val currentProfile: Flow<String> = dataStore.data.map { it[PreferencesKeys.CURRENT_PROFILE] ?: "Default" }
    
    suspend fun setCurrentProfile(profile: String) {
        dataStore.edit { it[PreferencesKeys.CURRENT_PROFILE] = profile }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val profileImage: Flow<String?> = currentProfile.flatMapLatest { profile ->
        dataStore.data.map { it[PreferencesKeys.profileImageKey(profile)] }
    }
    
    suspend fun setProfileImage(path: String?, profileName: String? = null) {
        dataStore.edit { prefs ->
            val profile = profileName ?: prefs[PreferencesKeys.CURRENT_PROFILE] ?: "Default"
            val key = PreferencesKeys.profileImageKey(profile)
            if (path == null) prefs.remove(key)
            else prefs[key] = path
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeCategoryId: Flow<String?> = currentProfile.flatMapLatest { profile ->
        dataStore.data.map { it[PreferencesKeys.activeCategoryIdKey(profile)] }
    }

    suspend fun setActiveCategoryId(categoryId: String?, profileName: String? = null) {
        dataStore.edit { prefs ->
            val profile = profileName ?: prefs[PreferencesKeys.CURRENT_PROFILE] ?: "Default"
            val key = PreferencesKeys.activeCategoryIdKey(profile)
            if (categoryId == null) prefs.remove(key)
            else prefs[key] = categoryId
        }
    }

    /**
     * Body Settings
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val gender: Flow<Gender> = currentProfile.flatMapLatest { profile ->
        dataStore.data.map { prefs ->
            Gender.valueOf(prefs[PreferencesKeys.genderKey(profile)] ?: Gender.MALE.name)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bodyType: Flow<BodyType> = currentProfile.flatMapLatest { profile ->
        dataStore.data.map { prefs ->
            BodyType.valueOf(prefs[PreferencesKeys.bodyTypeKey(profile)] ?: BodyType.SLIM.name)
        }
    }

    val pdfQuality: Flow<com.example.chronomapscanner.data.domain.PdfQuality> = dataStore.data.map { prefs ->
        val qualityStr = prefs[PreferencesKeys.PDF_QUALITY] ?: com.example.chronomapscanner.data.domain.PdfQuality.MEDIUM.name
        try {
            com.example.chronomapscanner.data.domain.PdfQuality.valueOf(qualityStr)
        } catch (e: IllegalArgumentException) {
            com.example.chronomapscanner.data.domain.PdfQuality.MEDIUM
        }
    }

    val openPdfAutomatically: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.OPEN_PDF_AUTOMATICALLY] ?: true
    }

    val showExportDialog: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SHOW_EXPORT_DIALOG] ?: true
    }

    suspend fun setPdfQuality(quality: com.example.chronomapscanner.data.domain.PdfQuality) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.PDF_QUALITY] = quality.name
        }
    }

    suspend fun setOpenPdfAutomatically(open: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.OPEN_PDF_AUTOMATICALLY] = open
        }
    }

    suspend fun setShowExportDialog(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SHOW_EXPORT_DIALOG] = show
        }
    }

    suspend fun updateBodySettings(gender: Gender, bodyType: BodyType, profileName: String? = null) {
        dataStore.edit { prefs ->
            val profile = profileName ?: prefs[PreferencesKeys.CURRENT_PROFILE] ?: "Default"
            prefs[PreferencesKeys.genderKey(profile)] = gender.name
            prefs[PreferencesKeys.bodyTypeKey(profile)] = bodyType.name
        }
    }
    
    fun getGenderForProfile(profile: String): Flow<Gender> = dataStore.data.map { prefs ->
        Gender.valueOf(prefs[PreferencesKeys.genderKey(profile)] ?: Gender.MALE.name)
    }

    fun getBodyTypeForProfile(profile: String): Flow<BodyType> = dataStore.data.map { prefs ->
        BodyType.valueOf(prefs[PreferencesKeys.bodyTypeKey(profile)] ?: BodyType.SLIM.name)
    }
    
    fun getProfileImageForProfile(profile: String): Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.profileImageKey(profile)]
    }

    /**
     * Reminder Settings
     */
    val remindersEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.REMINDERS_ENABLED] ?: false }
    val remindersValue: Flow<Int> = dataStore.data.map { it[PreferencesKeys.REMINDERS_VALUE] ?: 1 }
    val remindersUnit: Flow<ReminderUnit> = dataStore.data.map { prefs ->
        ReminderUnit.valueOf(prefs[PreferencesKeys.REMINDERS_UNIT] ?: ReminderUnit.MONTHS.name)
    }
    val lastReminderDate: Flow<String?> = dataStore.data.map { it[PreferencesKeys.LAST_REMINDER_DATE] }

    val isImporting = MutableStateFlow(false)

    suspend fun updateReminderSettings(enabled: Boolean, value: Int, unit: ReminderUnit, lastDate: String? = null) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.REMINDERS_ENABLED] = enabled
            prefs[PreferencesKeys.REMINDERS_VALUE] = value
            prefs[PreferencesKeys.REMINDERS_UNIT] = unit.name
            if (lastDate != null) prefs[PreferencesKeys.LAST_REMINDER_DATE] = lastDate
        }
    }
    
    suspend fun renameProfileSettings(oldName: String, newName: String) {
        dataStore.edit { prefs ->
            val oldGender = prefs[PreferencesKeys.genderKey(oldName)]
            if (oldGender != null) {
                prefs[PreferencesKeys.genderKey(newName)] = oldGender
                prefs.remove(PreferencesKeys.genderKey(oldName))
            }
            
            val oldBodyType = prefs[PreferencesKeys.bodyTypeKey(oldName)]
            if (oldBodyType != null) {
                prefs[PreferencesKeys.bodyTypeKey(newName)] = oldBodyType
                prefs.remove(PreferencesKeys.bodyTypeKey(oldName))
            }
            
            val oldImage = prefs[PreferencesKeys.profileImageKey(oldName)]
            if (oldImage != null) {
                prefs[PreferencesKeys.profileImageKey(newName)] = oldImage
                prefs.remove(PreferencesKeys.profileImageKey(oldName))
            }

            val oldCategoryId = prefs[PreferencesKeys.activeCategoryIdKey(oldName)]
            if (oldCategoryId != null) {
                prefs[PreferencesKeys.activeCategoryIdKey(newName)] = oldCategoryId
                prefs.remove(PreferencesKeys.activeCategoryIdKey(oldName))
            }
        }
    }

    /**
     * Color Visibility Settings
     */
    val colorSettings: Flow<List<ColorSetting>> = dataStore.data.map { prefs ->
        defaultColorSettings.map { setting ->
            val key = booleanPreferencesKey(PreferencesKeys.COLOR_VISIBILITY_PREFIX + setting.hex)
            setting.copy(visible = prefs[key] ?: true)
        }
    }

    suspend fun toggleColorVisibility(hex: String) {
        dataStore.edit { prefs ->
            val key = booleanPreferencesKey(PreferencesKeys.COLOR_VISIBILITY_PREFIX + hex)
            val current = prefs[key] ?: true
            prefs[key] = !current
        }
    }

    /**
     * Rapid Modes Settings
     */
    val keepLegendVisible: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.KEEP_LEGEND_VISIBLE] ?: false }
    val rapidInsertionMode: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.RAPID_INSERTION] ?: false }
    val rapidUpdateMode: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.RAPID_UPDATE] ?: false }

    suspend fun updateRapidModes(keepLegend: Boolean, rapidInsert: Boolean, rapidUpdate: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEEP_LEGEND_VISIBLE] = keepLegend
            prefs[PreferencesKeys.RAPID_INSERTION] = rapidInsert
            prefs[PreferencesKeys.RAPID_UPDATE] = rapidUpdate
        }
    }

    /**
     * Interface Settings
     */
    val showZoomButton: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.SHOW_ZOOM_BUTTON] ?: false }

    suspend fun updateShowZoomButton(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SHOW_ZOOM_BUTTON] = show
        }
    }

    /**
     * Scanner Settings
     */
    val scannerDelayMs: Flow<Long> = dataStore.data.map { it[PreferencesKeys.SCANNER_DELAY_MS] ?: 500L }
    suspend fun setScannerDelayMs(delay: Long) {
        dataStore.edit { it[PreferencesKeys.SCANNER_DELAY_MS] = delay }
    }

    val scannerIntervalMin: Flow<Long> = dataStore.data.map { it[PreferencesKeys.SCANNER_INTERVAL_MIN] ?: 5L }
    suspend fun setScannerIntervalMin(interval: Long) {
        dataStore.edit { it[PreferencesKeys.SCANNER_INTERVAL_MIN] = interval }
    }
    
    val warnOnEmptyMoleDeletion: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.WARN_ON_EMPTY_MOLE_DELETION] ?: true }
    suspend fun setWarnOnEmptyMoleDeletion(warn: Boolean) {
        dataStore.edit { it[PreferencesKeys.WARN_ON_EMPTY_MOLE_DELETION] = warn }
    }

    val snapToRecentOnAddMole: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.SNAP_TO_RECENT_ON_ADD_MOLE] ?: true }
    suspend fun setSnapToRecentOnAddMole(snap: Boolean) {
        dataStore.edit { it[PreferencesKeys.SNAP_TO_RECENT_ON_ADD_MOLE] = snap }
    }
}
