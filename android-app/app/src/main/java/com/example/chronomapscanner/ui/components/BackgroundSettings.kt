package com.example.chronomapscanner.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.chronomapscanner.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.chronomapscanner.data.local.room.BackgroundCategoryEntity
import com.example.chronomapscanner.data.local.room.BackgroundVariantEntity
import com.example.chronomapscanner.ui.NameEditDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BackgroundSettings(
    categories: List<BackgroundCategoryEntity>,
    activeCategoryId: String?,
    onSetActiveCategory: (String, Boolean) -> Unit, // categoryId, keepMoles
    selectedCategoryId: String?,
    onSelectCategoryForEditing: (String?) -> Unit,
    variantsForSelectedCategory: List<BackgroundVariantEntity>,
    onAddVariants: (String, List<Uri>) -> Unit,
    onAddSingleVariant: (String, String, Uri) -> Unit,
    onEditVariantImage: (String) -> Unit,
    onUpdateVariantName: (BackgroundVariantEntity, String) -> Unit,
    onUpdateVariantsOrder: (List<BackgroundVariantEntity>) -> Unit,
    onDeleteVariant: (BackgroundVariantEntity) -> Unit,
    userSettings: com.example.chronomapscanner.data.domain.UserSettings,
    onSaveBodySettings: (com.example.chronomapscanner.data.domain.Gender, com.example.chronomapscanner.data.domain.BodyType) -> Unit
) {
    var showMigrationDialog by remember { mutableStateOf<String?>(null) } // categoryId to switch to
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) } // categoryId to switch to

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.bg_model_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.bg_model_desc), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        // Find active category
        val activeCategory = categories.find { it.id == activeCategoryId }
        val sortedCategories = categories.sortedByDescending { it.name == "Personalizzato" } // Personalizzato in cima

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = activeCategory?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.bg_model_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sortedCategories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            expanded = false
                            if (category.id != activeCategoryId) {
                                showMigrationDialog = category.id
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (activeCategory?.name == "Persona") {
            com.example.chronomapscanner.ui.BodyProfileSettings(
                settings = userSettings,
                onSave = onSaveBodySettings
            )
        } else if (activeCategory?.name == "Personalizzato") {
            // Variants Editor for Personalizzato
            Text(stringResource(R.string.bg_pages_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.bg_pages_desc), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            var localVariants by remember(variantsForSelectedCategory) { mutableStateOf(variantsForSelectedCategory) }
            LaunchedEffect(variantsForSelectedCategory) {
                // Se la lista originale dal DB cambia in modo significativo (es. aggiunta/eliminazione),
                // aggiorniamo la lista locale, ma non lo facciamo mentre c'è un drag in corso.
                if (localVariants.size != variantsForSelectedCategory.size) {
                    localVariants = variantsForSelectedCategory
                }
            }

            val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                localVariants = localVariants.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            }
            
            LaunchedEffect(reorderableState.isAnyItemDragging) {
                if (!reorderableState.isAnyItemDragging && localVariants != variantsForSelectedCategory) {
                    onUpdateVariantsOrder(localVariants)
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(localVariants, key = { it.id }) { variant ->
                    ReorderableItem(reorderableState, variant.id) { isDragging ->
                        VariantListItem(
                            variant = variant,
                            isDragging = isDragging,
                            onDragModifier = Modifier.draggableHandle(),
                            onUpdateName = { name -> onUpdateVariantName(variant, name) },
                            onEditImage = { variant.imagePath?.let { onEditVariantImage(it) } },
                            onDelete = { onDeleteVariant(variant) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                item {
                    AddVariantButton(
                        onAddSingle = { name, uri -> onAddSingleVariant(activeCategory.id, name, uri) },
                        onAddMultiple = { uris -> onAddVariants(activeCategory.id, uris) }
                    )
                }
            }
        }
    }

    if (showMigrationDialog != null) {
        AlertDialog(
            onDismissRequest = { showMigrationDialog = null },
            title = { Text(stringResource(R.string.warning_title)) },
            text = { Text(stringResource(R.string.bg_change_warning_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    val targetId = showMigrationDialog!!
                    showMigrationDialog = null
                    onSetActiveCategory(targetId, true) // Mantienili -> Keep moles
                }) {
                    Text(stringResource(R.string.bg_keep_defects))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val targetId = showMigrationDialog!!
                    showMigrationDialog = null
                    showDeleteConfirmDialog = targetId
                }) {
                    Text(stringResource(R.string.bg_delete_defects))
                }
            }
        )
    }

    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(stringResource(R.string.warning_title)) },
            text = { Text(stringResource(R.string.bg_change_irreversible)) },
            confirmButton = {
                TextButton(onClick = {
                    val targetId = showDeleteConfirmDialog!!
                    showDeleteConfirmDialog = null
                    onSetActiveCategory(targetId, false) // Eliminali -> Don't keep moles
                }) {
                    Text(stringResource(R.string.ok_red), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun VariantListItem(
    variant: BackgroundVariantEntity,
    isDragging: Boolean,
    onDragModifier: Modifier,
    onUpdateName: (String) -> Unit,
    onEditImage: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditMenu by remember { mutableStateOf(false) }

    val elevation = if (isDragging) 8.dp else 0.dp
    
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            if (variant.imagePath != null) {
                AsyncImage(
                    model = variant.imagePath,
                    contentDescription = stringResource(R.string.desc_background_image_named, variant.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = stringResource(R.string.desc_background_image), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                }
            }
            
            // Gradient overlay for better text/icon visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            // Drag handle top-left
            Icon(
                Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.desc_reorder),
                modifier = onDragModifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                tint = androidx.compose.ui.graphics.Color.White
            )
            
            // Edit button top-right (on top of thumbnail)
            IconButton(
                onClick = { showEditMenu = true },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.desc_edit_variant), tint = androidx.compose.ui.graphics.Color.White)
            }
            
            // Name bottom-left
            Text(
                text = variant.name,
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }

    if (showEditMenu) {
        VariantEditDialog(
            variant = variant,
            onDismiss = { showEditMenu = false },
            onUpdateName = { 
                onUpdateName(it)
                showEditMenu = false
            },
            onEditImage = {
                onEditImage()
                showEditMenu = false
            },
            onDelete = {
                onDelete()
                showEditMenu = false
            }
        )
    }
}

@Composable
fun VariantEditDialog(
    variant: BackgroundVariantEntity,
    onDismiss: () -> Unit,
    onUpdateName: (String) -> Unit,
    onEditImage: () -> Unit,
    onDelete: () -> Unit
) {
    var nameText by remember { mutableStateOf(variant.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.bg_delete_title)) },
            text = { Text(stringResource(R.string.bg_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.bg_edit_variant_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text(stringResource(R.string.bg_variant_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    Button(
                        onClick = onEditImage,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = stringResource(R.string.desc_crop_image))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bg_crop_image))
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.desc_delete_image))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bg_delete_variant))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onUpdateName(nameText) }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVariantButton(onAddSingle: (String, Uri) -> Unit, onAddMultiple: (List<Uri>) -> Unit) {
    var showPhotoMenu by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingSingleUri by remember { mutableStateOf<Uri?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                pendingSingleUri = uris.first()
                showNameDialog = true
            } else {
                onAddMultiple(uris)
            }
        }
    }
    
    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingSingleUri != null) {
            showNameDialog = true
        } else {
            pendingSingleUri = null
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val fileProviderAuthority = "${context.packageName}.fileprovider"

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val cameraDir = java.io.File(context.cacheDir, "camera_images")
            cameraDir.mkdirs()
            val tmpFile = java.io.File(cameraDir, "camera_tmp_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(context, fileProviderAuthority, tmpFile)
            pendingSingleUri = uri
            takePhotoLauncher.launch(uri)
        }
    }

    val addVariantStr = stringResource(R.string.add_variant)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(
                onClickLabel = addVariantStr,
                role = androidx.compose.ui.semantics.Role.Button
            ) { showPhotoMenu = true }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_variant), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_variant), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }

    if (showPhotoMenu) {
        ModalBottomSheet(onDismissRequest = { showPhotoMenu = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp, start = 20.dp, end = 20.dp)) {
                Text(stringResource(R.string.new_photo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                val takePhotoStr = stringResource(R.string.take_photo)
                ListItem(
                    headlineContent = { Text(takePhotoStr) },
                    leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = takePhotoStr) },
                    modifier = Modifier.clickable(
                        onClickLabel = takePhotoStr,
                        role = androidx.compose.ui.semantics.Role.Button
                    ) {
                        showPhotoMenu = false
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            val cameraDir = java.io.File(context.cacheDir, "camera_images")
                            cameraDir.mkdirs()
                            val tmpFile = java.io.File(cameraDir, "camera_tmp_${System.currentTimeMillis()}.jpg")
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, fileProviderAuthority, tmpFile)
                            pendingSingleUri = uri
                            takePhotoLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    }
                )
                val chooseGalleryStr = stringResource(R.string.choose_gallery)
                ListItem(
                    headlineContent = { Text(chooseGalleryStr) },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = chooseGalleryStr) },
                    modifier = Modifier.clickable(
                        onClickLabel = chooseGalleryStr,
                        role = androidx.compose.ui.semantics.Role.Button
                    ) {
                        showPhotoMenu = false
                        galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }
        }
    }

    if (showNameDialog && pendingSingleUri != null) {
        NameEditDialog(
            title = "Nome Variante",
            initialName = "",
            onDismiss = { 
                showNameDialog = false
                pendingSingleUri = null
            },
            onConfirm = { name ->
                onAddSingle(name, pendingSingleUri!!)
                showNameDialog = false
                pendingSingleUri = null
            }
        )
    }
}
