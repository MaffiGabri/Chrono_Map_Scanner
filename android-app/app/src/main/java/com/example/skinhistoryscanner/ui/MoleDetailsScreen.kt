package com.example.skinhistoryscanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.skinhistoryscanner.R
import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.ui.components.DateHeader
import com.example.skinhistoryscanner.ui.components.HistoryItem
import com.example.skinhistoryscanner.ui.components.MoleSummaryHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.File
import androidx.compose.ui.res.stringResource
import com.example.skinhistoryscanner.utils.getLocalizedColorLabel

import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoleDetailsScreen(
    state: MoleDetailsUiState,
    warnOnEmptyMoleDeletion: Boolean,
    onSetWarnOnEmptyMoleDeletion: (Boolean) -> Unit,
    pendingPhotoPath: String? = null,
    editingEntryId: String? = null,
    autoCamera: Boolean = false,
    onClearPendingPhoto: () -> Unit = {},
    onBack: () -> Unit,
    onDeleteMole: () -> Unit,
    onOpenSplitView: () -> Unit,
    onAddPhoto: (String?) -> Unit,
    onPickFromGalleryUri: (String?, android.net.Uri) -> Unit,
    onReposition: () -> Unit,
    onUpdateColor: (String) -> Unit,
    onAddHistoryEntry: (LocalDate, String?, String?) -> Unit,
    onUpdateHistoryEntry: (String, LocalDate, String?, String?) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit
) {
    var showEmptyWarningDialog by remember { mutableStateOf(false) }

    val handleBack = {
        if (state.mole?.history?.isEmpty() == true && warnOnEmptyMoleDeletion) {
            showEmptyWarningDialog = true
        } else {
            onBack()
        }
    }

    BackHandler {
        handleBack()
    }

    if (state.mole == null) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(500)
            onBack()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val mole = state.mole
    var showColorPicker by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var selectedNoteDate by remember { mutableStateOf(LocalDate.now()) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onPickFromGalleryUri(editingEntry?.id, it) }
    }

    val latestPhoto = mole.history.filter { it.imagePath != null }.maxByOrNull { it.date }

    var hasAutoCameraFired by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoCamera, state.mole) {
        if (autoCamera && !hasAutoCameraFired && state.mole != null) {
            hasAutoCameraFired = true
            onAddPhoto(null)
        }
    }

    LaunchedEffect(pendingPhotoPath, editingEntryId) {
        if (pendingPhotoPath != null) {
            if (editingEntryId != null) {
                editingEntry = mole.history.find { it.id == editingEntryId }
                selectedNoteDate = editingEntry?.date ?: LocalDate.now()
            } else {
                editingEntry = null
                selectedNoteDate = LocalDate.now()
            }
            showNoteDialog = true
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.mole_details), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onReposition) {
                        Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.update_position))
                    }
                    IconButton(onClick = onOpenSplitView) {
                        Icon(Icons.Default.Compare, contentDescription = stringResource(R.string.split_view_title))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    editingEntry = null
                    showPhotoMenu = true 
                },
                icon = { Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(R.string.new_photo)) },
                text = { Text(stringResource(R.string.new_photo)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        val groupedHistory = remember(mole.history) {
            mole.history.sortedByDescending { it.date }.groupBy { it.date }
        }

        var fullScreenPhoto by remember { mutableStateOf<String?>(null) }
        var pendingDeleteEntryId by remember { mutableStateOf<String?>(null) }
        var showLastEntryDeleteWarning by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            item {
                val colorLabelKey = state.colorSettings.find { it.hex == mole.color }?.label ?: "color_other"
                val colorLabel = getLocalizedColorLabel(colorLabelKey)
                
                MoleSummaryHeader(
                    mole = mole,
                    variant = state.variant,
                    userSettings = state.userSettings,
                    colorLabel = colorLabel,
                    latestPhoto = latestPhoto,
                    onColorClick = { showColorPicker = true },
                    onPhotoClick = { fullScreenPhoto = it },
                    onEditPhoto = {
                        if (latestPhoto != null) {
                            editingEntry = latestPhoto
                            selectedNoteDate = latestPhoto.date
                            showNoteDialog = true
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    }
                    TextButton(onClick = { 
                        editingEntry = null
                        selectedNoteDate = LocalDate.now()
                        showNoteDialog = true 
                    }) {
                        Icon(Icons.Default.EditNote, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_note))
                    }
                }
            }

            groupedHistory.forEach { (date, entries) ->
                item {
                    DateHeader(date)
                }
                // ⚡ Bolt Optimization: Use `key` to prevent unnecessary recompositions when entries are reordered or deleted
                items(entries, key = { it.id }) { entry ->
                    HistoryItem(
                        entry = entry,
                        onClick = {
                            editingEntry = entry
                            selectedNoteDate = entry.date
                            showNoteDialog = true
                        },
                        onDelete = {
                            if (mole.history.size == 1 && warnOnEmptyMoleDeletion) {
                                pendingDeleteEntryId = entry.id
                                showLastEntryDeleteWarning = true
                            } else {
                                onDeleteHistoryEntry(entry.id)
                            }
                        },
                        onPhotoClick = { fullScreenPhoto = it }
                    )
                }
            }
            
            item {
                Spacer(Modifier.height(100.dp))
            }
        }

        if (showLastEntryDeleteWarning) {
            AlertDialog(
                onDismissRequest = { showLastEntryDeleteWarning = false },
                title = { Text("Attenzione: Ultimo elemento", fontWeight = FontWeight.Bold) },
                text = { 
                    Text("Stai per eliminare l'ultima nota o foto di questo difetto. Senza elementi, il difetto verrà rimosso dalla mappa per non sporcare il database. Vuoi procedere?") 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingDeleteEntryId?.let { onDeleteHistoryEntry(it) }
                            showLastEntryDeleteWarning = false
                            pendingDeleteEntryId = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Elimina")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showLastEntryDeleteWarning = false
                        pendingDeleteEntryId = null
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (fullScreenPhoto != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { fullScreenPhoto = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    ZoomableImage(imagePath = fullScreenPhoto)
                    IconButton(
                        onClick = { fullScreenPhoto = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha=0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                    }
                }
            }
        }

        if (showPhotoMenu) {
            ModalBottomSheet(onDismissRequest = { showPhotoMenu = false }) {
                Column(modifier = Modifier.padding(bottom = 32.dp, start = 20.dp, end = 20.dp)) {
                    Text(stringResource(R.string.new_photo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.take_photo)) },
                        leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.take_photo)) },
                        modifier = Modifier.clickable { 
                            showPhotoMenu = false
                            onAddPhoto(editingEntry?.id) 
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.choose_gallery)) },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.choose_gallery)) },
                        modifier = Modifier.clickable { 
                            showPhotoMenu = false
                            galleryLauncher.launch("image/*")
                        }
                    )
                }
            }
        }

        if (showColorPicker) {
            ColorPickerOverlay(
                colorSettings = state.colorSettings,
                currentMoleColor = mole.color,
                onColorSelected = {
                    onUpdateColor(it)
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }

        if (showNoteDialog) {
            NoteDialog(
                initialDate = selectedNoteDate,
                initialNotes = editingEntry?.notes ?: "",
                pendingPhotoPath = pendingPhotoPath ?: editingEntry?.imagePath,
                isEditing = editingEntry != null,
                onDismiss = { 
                    showNoteDialog = false
                    editingEntry = null
                    onClearPendingPhoto()
                },
                onSave = { date, notes ->
                    val entry = editingEntry
                    if (entry != null) {
                        onUpdateHistoryEntry(entry.id, date, notes, pendingPhotoPath)
                    } else {
                        onAddHistoryEntry(date, notes, pendingPhotoPath)
                    }
                    showNoteDialog = false
                    editingEntry = null
                    onClearPendingPhoto()
                },
                onChangePhoto = {
                    showNoteDialog = false
                    showPhotoMenu = true
                }
            )
        }

        if (showDeleteConfirm) {
            DeleteConfirmDialog(
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    onDeleteMole()
                }
            )
        }

        if (showEmptyWarningDialog) {
            var dontShowAgain by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { showEmptyWarningDialog = false },
                title = { Text("Difetto vuoto", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Stai uscendo da questo difetto senza aver inserito note o foto. Poiché è vuoto, verrà eliminato definitivamente dalla mappa per non sporcare il database.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dontShowAgain = !dontShowAgain }
                        ) {
                            Checkbox(
                                checked = dontShowAgain,
                                onCheckedChange = { dontShowAgain = it }
                            )
                            Text("Non ricordarmelo più", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showEmptyWarningDialog = false
                        if (dontShowAgain) {
                            onSetWarnOnEmptyMoleDeletion(false)
                        }
                        onBack()
                    }) {
                        Text("Procedi")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyWarningDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun ColorPickerOverlay(
    colorSettings: List<com.example.skinhistoryscanner.data.domain.ColorSetting>,
    currentMoleColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                colorSettings.forEach { setting ->
                    val isSelected = setting.hex == currentMoleColor
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable { onColorSelected(setting.hex) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(android.graphics.Color.parseColor(setting.hex)), CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            getLocalizedColorLabel(setting.label),
                            style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDialog(
    initialDate: LocalDate,
    initialNotes: String = "",
    pendingPhotoPath: String? = null,
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String) -> Unit,
    onChangePhoto: () -> Unit = {}
) {
    var noteText by remember { mutableStateOf(initialNotes) }
    var noteDate by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = noteDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        noteDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = when {
                    isEditing -> stringResource(R.string.note_title_edit)
                    pendingPhotoPath != null -> stringResource(R.string.note_title_photo)
                    else -> stringResource(R.string.note_title_new)
                }, 
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column {
                if (pendingPhotoPath != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(pendingPhotoPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = onChangePhoto,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.history), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.date_label, noteDate.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault()))))
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(noteDate, noteText) },
                enabled = pendingPhotoPath != null || noteText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun DeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.delete_mole_confirm)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.delete_forever)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
