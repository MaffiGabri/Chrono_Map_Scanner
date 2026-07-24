package com.example.chronomapscanner.ui

import android.content.Intent
import android.provider.CalendarContract
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.example.chronomapscanner.R
import com.example.chronomapscanner.data.domain.BodyType
import com.example.chronomapscanner.data.domain.Gender
import com.example.chronomapscanner.data.domain.ReminderUnit
import com.example.chronomapscanner.data.domain.UserSettings
import com.example.chronomapscanner.data.domain.ReminderSettings
import java.io.File

enum class SettingsView {
    MAIN, BACKGROUND, DATABASE, PROFILES_MGMT, ABOUT, REMINDERS, RAPID_MODES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onUpdateProfileInfo: (String, String, String?) -> Unit,
    onCreateProfile: (String) -> Unit,
    onSaveBodySettings: (Gender, BodyType) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onExportDatabase: (String, android.net.Uri) -> Unit,
    onImportDatabaseUri: (android.net.Uri) -> Unit,
    onPickProfileImageUri: (android.net.Uri) -> Unit,
    onUpdateReminders: (Boolean, Int, ReminderUnit) -> Unit,
    onUpdateRapidModes: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUpdateShowZoomButton: (Boolean) -> Unit,
    onUpdateScannerSettings: (Long, Long) -> Unit,
    onUpdateWarnOnEmptyMoleDeletion: (Boolean) -> Unit,
    onDebugSeed: () -> Unit,
    onTestNotification: () -> Unit,
    backgroundSettingsContent: @Composable () -> Unit
) {
    var currentView by remember { mutableStateOf(SettingsView.MAIN) }
    var showNameEditDialog by remember { mutableStateOf(false) }
    var pendingExportProfile by remember { mutableStateOf<String?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { onPickProfileImageUri(it) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            cropLauncher.launch(
                CropImageContractOptions(
                    uri = it,
                    cropImageOptions = CropImageOptions(
                        aspectRatioX = 1,
                        aspectRatioY = 1,
                        fixAspectRatio = true
                    )
                )
            )
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportDatabaseUri(it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { destinationUri ->
            val profile = pendingExportProfile ?: return@rememberLauncherForActivityResult
            onExportDatabase(profile, destinationUri)
        }
        pendingExportProfile = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when(currentView) {
                            SettingsView.MAIN -> stringResource(R.string.settings_title_main)
                            SettingsView.BACKGROUND -> stringResource(R.string.settings_title_background)
                            SettingsView.DATABASE -> stringResource(R.string.settings_title_database)
                            SettingsView.PROFILES_MGMT -> stringResource(R.string.settings_title_profiles)
                            SettingsView.ABOUT -> stringResource(R.string.settings_title_about)
                            SettingsView.REMINDERS -> stringResource(R.string.settings_title_reminders)
                            SettingsView.RAPID_MODES -> stringResource(R.string.settings_title_rapid_modes)
                        },
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentView == SettingsView.MAIN) onBack() else currentView = SettingsView.MAIN
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = currentView,
                transitionSpec = {
                    if (targetState != SettingsView.MAIN) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "settings_nav"
            ) { view ->
                when (view) {
                    SettingsView.MAIN -> MainSettings(
                        state = state,
                        onEditName = { showNameEditDialog = true },
                        onPickImage = { galleryLauncher.launch("image/*") },
                        onNavigate = { currentView = it }
                    )
                    SettingsView.BACKGROUND -> backgroundSettingsContent()
                    SettingsView.DATABASE -> DatabaseSettings(
                        currentProfile = state.profileName,
                        scannerDelayMs = state.scannerDelayMs,
                        scannerIntervalMin = state.scannerIntervalMin,
                        onExport = {
                            pendingExportProfile = it
                            exportLauncher.launch("${it}_backup.zip")
                        },
                        onImport = {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                        onUpdateScannerSettings = onUpdateScannerSettings,
                        warnOnEmptyMoleDeletion = state.warnOnEmptyMoleDeletion,
                        onUpdateWarnOnEmptyMoleDeletion = onUpdateWarnOnEmptyMoleDeletion,
                        onDebugSeed = onDebugSeed
                    )
                    SettingsView.PROFILES_MGMT -> ProfilesManagement(
                        currentProfile = state.profileName,
                        profiles = state.profiles,
                        onSwitch = onSwitchProfile,
                        onDelete = onDeleteProfile,
                        onCreate = onCreateProfile
                    )
                    SettingsView.ABOUT -> AboutScreen()
                    SettingsView.REMINDERS -> RemindersSettings(
                        settings = state.reminderSettings,
                        onUpdate = onUpdateReminders,
                        onTestNotification = onTestNotification
                    )
                    SettingsView.RAPID_MODES -> RapidModesSettings(
                        keepLegendVisible = state.keepLegendVisible,
                        rapidInsertionMode = state.rapidInsertionMode,
                        rapidUpdateMode = state.rapidUpdateMode,
                        snapToRecentOnAddMole = state.snapToRecentOnAddMole,
                        showZoomButton = state.showZoomButton,
                        onUpdate = onUpdateRapidModes,
                        onUpdateZoom = onUpdateShowZoomButton
                    )
                }
            }
        }

        if (showNameEditDialog) {
            NameEditDialog(
                initialName = state.profileName,
                onDismiss = { showNameEditDialog = false },
                onConfirm = { newName -> 
                    onUpdateProfileInfo(state.profileName, newName, null)
                    showNameEditDialog = false
                }
            )
        }
    }
}

@Composable
fun MainSettings(
    state: SettingsUiState,
    onEditName: () -> Unit,
    onPickImage: () -> Unit,
    onNavigate: (SettingsView) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Profile Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = stringResource(R.string.change_profile_photo_desc),
                            role = androidx.compose.ui.semantics.Role.Button
                        ) { onPickImage() }
                ) {
                    if (state.profileImage != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(state.profileImage))
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.settings_profile_photo_desc),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person, 
                                null, 
                                modifier = Modifier.size(40.dp), 
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                Spacer(Modifier.width(20.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.profileName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onEditName, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.edit), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        stringResource(R.string.mapped_moles, state.moleCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Menu Items
        SettingsMenuItem(
            title = stringResource(R.string.settings_title_background),
            subtitle = stringResource(R.string.settings_background_subtitle),
            icon = Icons.Default.Wallpaper,
            onClick = { onNavigate(SettingsView.BACKGROUND) }
        )
        SettingsMenuItem(
            title = stringResource(R.string.reminders),
            subtitle = stringResource(R.string.enable_reminders),
            icon = Icons.Default.NotificationsActive,
            onClick = { onNavigate(SettingsView.REMINDERS) }
        )
        
        SettingsMenuItem(
            title = stringResource(R.string.settings_title_rapid_modes),
            subtitle = stringResource(R.string.rapid_modes_subtitle),
            icon = Icons.Default.Bolt,
            onClick = { onNavigate(SettingsView.RAPID_MODES) }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Language Switcher
        var currentLanguage by remember { 
            mutableStateOf(
                androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags().let {
                    if (it.isEmpty()) "system" else it
                }
            )
        }
        var showLanguageMenu by remember { mutableStateOf(false) }

        val appLanguageStr = stringResource(R.string.app_language)
        ListItem(
            headlineContent = { Text(appLanguageStr, fontWeight = FontWeight.SemiBold) },
            supportingContent = { 
                Text(
                    when (currentLanguage) {
                        "it" -> stringResource(R.string.lang_it)
                        "en" -> stringResource(R.string.lang_en)
                        else -> stringResource(R.string.lang_system)
                    }
                ) 
            },
            leadingContent = { Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable(
                onClickLabel = appLanguageStr,
                role = androidx.compose.ui.semantics.Role.Button
            ) { showLanguageMenu = true }
        )

        if (showLanguageMenu) {
            AlertDialog(
                onDismissRequest = { showLanguageMenu = false },
                title = { Text(stringResource(R.string.app_language)) },
                text = {
                    Column {
                        val options = listOf(
                            "system" to stringResource(R.string.lang_system),
                            "it" to stringResource(R.string.lang_it),
                            "en" to stringResource(R.string.lang_en)
                        )
                        options.forEach { (tag, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        onClickLabel = label,
                                        role = androidx.compose.ui.semantics.Role.Button
                                    ) {
                                        currentLanguage = tag
                                        val localeList = if (tag == "system") {
                                            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                                        } else {
                                            androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                                        }
                                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
                                        showLanguageMenu = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = currentLanguage == tag, onClick = null)
                                Spacer(Modifier.width(16.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageMenu = false }) { Text(stringResource(R.string.close)) }
                }
            )
        }
        SettingsMenuItem(
            title = stringResource(R.string.settings_title_database),
            subtitle = stringResource(R.string.database_subtitle),
            icon = Icons.Default.Storage,
            onClick = { onNavigate(SettingsView.DATABASE) }
        )
        SettingsMenuItem(
            title = stringResource(R.string.settings_title_profiles),
            subtitle = stringResource(R.string.profiles_subtitle),
            icon = Icons.Default.People,
            onClick = { onNavigate(SettingsView.PROFILES_MGMT) }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        
        SettingsMenuItem(
            title = stringResource(R.string.settings_title_about),
            subtitle = stringResource(R.string.about_subtitle),
            icon = Icons.Default.Info,
            onClick = { onNavigate(SettingsView.ABOUT) }
        )
    }
}

@Composable
fun SettingsMenuItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(
            onClickLabel = title,
            role = androidx.compose.ui.semantics.Role.Button
        ) { onClick() }
    )
}

@Composable
fun BodyProfileSettings(settings: UserSettings, onSave: (Gender, BodyType) -> Unit) {
    var gender by remember { mutableStateOf(settings.gender) }
    var bodyType by remember { mutableStateOf(settings.bodyType) }

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.app_info_title), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.body_profile_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.body_type), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = gender == Gender.MALE,
                    onClick = { gender = Gender.MALE; onSave(Gender.MALE, bodyType) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.male)) }
                SegmentedButton(
                    selected = gender == Gender.FEMALE,
                    onClick = { gender = Gender.FEMALE; onSave(Gender.FEMALE, bodyType) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.female)) }
            }

            Text(stringResource(R.string.body_type), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = bodyType == BodyType.SLIM,
                    onClick = { bodyType = BodyType.SLIM; onSave(gender, BodyType.SLIM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.average_build)) }
                SegmentedButton(
                    selected = bodyType == BodyType.OVERWEIGHT,
                    onClick = { bodyType = BodyType.OVERWEIGHT; onSave(gender, BodyType.OVERWEIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.overweight)) }
            }
        }
    }
}

@Composable
fun RemindersSettings(settings: ReminderSettings, onUpdate: (Boolean, Int, ReminderUnit) -> Unit, onTestNotification: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.enabled) }
    var valueStr by remember { mutableStateOf(settings.intervalValue.toString()) }
    var unit by remember { mutableStateOf(settings.intervalUnit) }
    val context = LocalContext.current
    
    var hasNotificationPermission by remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        } else {
            mutableStateOf(true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
            if (granted) {
                enabled = true
                onUpdate(true, valueStr.toIntOrNull() ?: 1, unit)
            } else {
                enabled = false
                onUpdate(false, valueStr.toIntOrNull() ?: 1, unit)
            }
        }
    )

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.reminders_desc), style = MaterialTheme.typography.bodyMedium)

        ListItem(
            headlineContent = { Text(stringResource(R.string.notification_permission_title), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.notification_permission_desc)) },
            trailingContent = {
                Switch(checked = enabled, onCheckedChange = { isChecked -> 
                    if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        enabled = isChecked
                        onUpdate(isChecked, valueStr.toIntOrNull() ?: 1, unit)
                    }
                })
            }
        )

        AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.reminder_interval), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = valueStr,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                valueStr = it
                                it.toIntOrNull()?.let { v -> onUpdate(enabled, v, unit) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.reminder_value_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1.5f)) {
                        SegmentedButton(
                            selected = unit == ReminderUnit.DAYS,
                            onClick = { 
                                unit = ReminderUnit.DAYS
                                onUpdate(enabled, valueStr.toIntOrNull() ?: 1, ReminderUnit.DAYS)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(stringResource(R.string.days)) }
                        SegmentedButton(
                            selected = unit == ReminderUnit.MONTHS,
                            onClick = { 
                                unit = ReminderUnit.MONTHS
                                onUpdate(enabled, valueStr.toIntOrNull() ?: 1, ReminderUnit.MONTHS)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(stringResource(R.string.months)) }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        val calendar = java.util.Calendar.getInstance()
                        val intervalVal = valueStr.toIntOrNull() ?: 1
                        if (unit == ReminderUnit.DAYS) {
                            calendar.add(java.util.Calendar.DAY_OF_YEAR, intervalVal)
                        } else {
                            calendar.add(java.util.Calendar.MONTH, intervalVal)
                        }
                        
                        val intent = Intent(Intent.ACTION_INSERT)
                            .setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendar.timeInMillis)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, calendar.timeInMillis)
                            .putExtra(CalendarContract.Events.TITLE, context.getString(R.string.calendar_event_title))
                            .putExtra(CalendarContract.Events.DESCRIPTION, context.getString(R.string.calendar_event_desc))
                            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Default.CalendarToday, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_to_calendar))
                }
                
                Button(
                    onClick = onTestNotification,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                ) {
                    Icon(Icons.Default.NotificationsActive, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.test_notification))
                }
            }
        }
    }
}

@Composable
fun RapidModesSettings(
    keepLegendVisible: Boolean,
    rapidInsertionMode: Boolean,
    rapidUpdateMode: Boolean,
    snapToRecentOnAddMole: Boolean,
    showZoomButton: Boolean,
    onUpdate: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUpdateZoom: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.rapid_modes_desc), style = MaterialTheme.typography.bodyMedium)

        ListItem(
            headlineContent = { Text(stringResource(R.string.keep_legend_visible), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.auto_close_panels_desc)) },
            trailingContent = {
                Switch(
                    checked = keepLegendVisible, 
                    onCheckedChange = { onUpdate(it, rapidInsertionMode, rapidUpdateMode, snapToRecentOnAddMole) }
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.rapid_insertion_mode), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.rapid_insertion_mode_desc)) },
            trailingContent = {
                Switch(
                    checked = rapidInsertionMode, 
                    onCheckedChange = { onUpdate(keepLegendVisible, it, rapidUpdateMode, snapToRecentOnAddMole) }
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.rapid_update_mode), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.rapid_update_mode_desc)) },
            trailingContent = {
                Switch(
                    checked = rapidUpdateMode, 
                    onCheckedChange = { onUpdate(keepLegendVisible, rapidInsertionMode, it, snapToRecentOnAddMole) }
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.snap_to_recent_on_add_title), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.snap_to_recent_on_add_subtitle)) },
            trailingContent = {
                Switch(
                    checked = snapToRecentOnAddMole, 
                    onCheckedChange = { onUpdate(keepLegendVisible, rapidInsertionMode, rapidUpdateMode, it) }
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.show_zoom_button), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.show_zoom_button_desc)) },
            trailingContent = {
                Switch(
                    checked = showZoomButton, 
                    onCheckedChange = onUpdateZoom
                )
            }
        )
    }
}

@Composable
fun DatabaseSettings(
    currentProfile: String,
    scannerDelayMs: Long,
    scannerIntervalMin: Long,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    onUpdateScannerSettings: (Long, Long) -> Unit,
    warnOnEmptyMoleDeletion: Boolean,
    onUpdateWarnOnEmptyMoleDeletion: (Boolean) -> Unit,
    onDebugSeed: () -> Unit
) {
    Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.database_desc), style = MaterialTheme.typography.bodyMedium)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.active_profile, currentProfile), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onExport(currentProfile) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_db))
                    }
                    Button(
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_db))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDebugSeed,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_reset_create_500_moles))
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            var showScannerInfoDialog by remember { mutableStateOf(false) }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.data_integrity_scanner_title), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showScannerInfoDialog = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.app_info_title), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.data_integrity_scanner_delay, scannerDelayMs), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = scannerDelayMs.toFloat(),
                    onValueChange = { onUpdateScannerSettings(it.toLong(), scannerIntervalMin) },
                    valueRange = 100f..2000f,
                    steps = 18
                )
                
                Spacer(Modifier.height(16.dp))
                val intervalText = if (scannerIntervalMin <= 0L) stringResource(R.string.interval_never) else stringResource(R.string.interval_mins, scannerIntervalMin)
                Text(stringResource(R.string.data_integrity_scanner_interval, intervalText), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = scannerIntervalMin.toFloat(),
                    onValueChange = { onUpdateScannerSettings(scannerDelayMs, it.toLong()) },
                    valueRange = 0f..60f,
                    steps = 59
                )
            }

            if (showScannerInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showScannerInfoDialog = false },
                    title = { Text(stringResource(R.string.data_integrity_scanner_info_title), fontWeight = FontWeight.Bold) },
                    text = { 
                        Text(stringResource(R.string.data_integrity_scanner_info_desc))
                    },
                    confirmButton = {
                        Button(onClick = { showScannerInfoDialog = false }) {
                            Text(stringResource(R.string.data_integrity_scanner_info_confirm))
                        }
                    }
                )
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.warn_empty_mole_deletion_title), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.warn_empty_mole_deletion_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = warnOnEmptyMoleDeletion,
                        onCheckedChange = { onUpdateWarnOnEmptyMoleDeletion(it) }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun ProfilesManagement(
    currentProfile: String, 
    profiles: List<String>, 
    onSwitch: (String) -> Unit, 
    onDelete: (String) -> Unit,
    onCreate: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp), 
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(stringResource(R.string.profiles_mgmt_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }
            // ⚡ Bolt Optimization: Use `key` to prevent unnecessary recompositions when profiles are added or deleted
            items(profiles, key = { it }) { profile ->
                val isActive = profile == currentProfile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            enabled = !isActive,
                            onClickLabel = stringResource(R.string.select_profile),
                            role = androidx.compose.ui.semantics.Role.Button
                        ) { onSwitch(profile) },
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = if (isActive) 4.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(profile, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyLarge)
                        if (!isActive) {
                            IconButton(onClick = { profileToDelete = profile }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        
        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.create_new_profile))
        }
        
        Text(
            text = stringResource(R.string.profiles_empty_deletion_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(20.dp))
    }

    if (showCreateDialog) {
        NameEditDialog(
            title = stringResource(R.string.add_profile),
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { 
                onCreate(it)
                showCreateDialog = false
            }
        )
    }

    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_confirm, profileToDelete!!)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(profileToDelete!!)
                        profileToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Architectural/Minimalist Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(2.dp)
                        .width(60.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = stringResource(R.string.version_label, "1.0.0"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        // Developer Profile Section (Mies van der Rohe inspired clean block)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp), // Sharp geometric corners
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                // Profile Image with architectural transparent integration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // We removed the harsh border and background block to allow the transparent
                    // PNG bust to blend seamlessly with the clean architectural background.
                    Image(
                        painter = painterResource(id = R.drawable.profile_gabriele),
                        contentDescription = stringResource(R.string.about_developer_name),
                        modifier = Modifier
                            .size(160.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Name and Role
                Text(
                    text = "GABRIELE MAFFIONE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Text(
                    text = stringResource(R.string.about_role).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Description
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 24.dp)
                )

                Spacer(Modifier.height(32.dp))

                // Links
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = { uriHandler.openUri("https://maffionegabriele.wordpress.com/") },
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.about_portfolio).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    TextButton(
                        onClick = { uriHandler.openUri("https://www.linkedin.com/in/gabriele-maffione") },
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.about_linkedin).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        Spacer(Modifier.height(32.dp))

        // Replaced Vibe Coding Badge with Jules and Antigravity
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(0.dp), // Architectural sharp corner
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = stringResource(R.string.about_subtitle),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.about_developed_with).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun NameEditDialog(
    title: String = stringResource(R.string.rename_profile),
    initialName: String, 
    onDismiss: () -> Unit, 
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.profile_name_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
