package com.example.chronomapscanner.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chronomapscanner.R
import com.example.chronomapscanner.data.local.room.BackgroundVariantEntity
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VariantManagementBottomSheet(
    variants: List<BackgroundVariantEntity>,
    onDismiss: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onUpdateDate: (String, LocalDate) -> Unit,
    onUpdateNotes: (String, String) -> Unit
) {
    var showDatePickerFor by remember { mutableStateOf<String?>(null) }
    var showNotesEditorFor by remember { mutableStateOf<String?>(null) }
    
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = "Gestisci Varianti",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(variants, key = { it.id }) { variant ->
                    ReorderableItem(reorderableState, variant.id) { isDragging ->
                        VariantItemRow(
                            variant = variant,
                            isDragging = isDragging,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onDragModifier = Modifier.draggableHandle(),
                            onEditDate = { showDatePickerFor = variant.id },
                            onEditNotes = { showNotesEditorFor = variant.id }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Date Picker Dialog
    if (showDatePickerFor != null) {
        val variantId = showDatePickerFor!!
        val variant = variants.find { it.id == variantId }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = variant?.dateAdded?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        onUpdateDate(variantId, newDate)
                    }
                    showDatePickerFor = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerFor = null }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Notes Editor Dialog
    if (showNotesEditorFor != null) {
        val variantId = showNotesEditorFor!!
        val variant = variants.find { it.id == variantId }
        var notesText by remember { mutableStateOf(variant?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showNotesEditorFor = null },
            title = { Text("Note Variante") },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Aggiungi nota") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateNotes(variantId, notesText)
                    showNotesEditorFor = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotesEditorFor = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun VariantItemRow(
    variant: BackgroundVariantEntity,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onDragModifier: Modifier,
    onEditDate: () -> Unit,
    onEditNotes: () -> Unit
) {
    val elevation = if (isDragging) 8.dp else 0.dp
    
    Surface(
        modifier = modifier,
        shadowElevation = elevation,
        color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Trascina per riordinare",
                modifier = onDragModifier.padding(end = 12.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = variant.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = variant.dateAdded.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!variant.notes.isNullOrBlank()) {
                    Text(
                        text = variant.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Row {
                IconButton(onClick = onEditDate) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Modifica Data")
                }
                IconButton(onClick = onEditNotes) {
                    Icon(Icons.Default.Notes, contentDescription = "Modifica Note")
                }
            }
        }
    }
}
