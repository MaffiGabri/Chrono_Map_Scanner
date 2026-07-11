package com.example.chronomapscanner.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.chronomapscanner.R
import com.example.chronomapscanner.ui.*
import com.example.chronomapscanner.ui.viewmodels.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChronoMapNavGraph(
    navController: androidx.navigation.NavHostController = androidx.navigation.compose.rememberNavController(),
    settingsViewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = BodyMapRoute) {
            
            composable<BodyMapRoute> { backStackEntry ->
                val bodyMapViewModel: BodyMapViewModel = hiltViewModel()
                val bodyMapState by bodyMapViewModel.bodyMapUiState.collectAsStateWithLifecycle()
                
                val variantManagementViewModel: VariantManagementViewModel = hiltViewModel()
                val variants by variantManagementViewModel.variants.collectAsStateWithLifecycle()
                
                val movingMoleId by backStackEntry.savedStateHandle.getStateFlow<String?>("movingMoleId", null).collectAsStateWithLifecycle()
                
                var showVariantMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                BodyMapScreen(
                    state = bodyMapState,
                    getThumbnail = { bodyMapViewModel.getThumbnail(it) },
                    onCycleVariant = { bodyMapViewModel.cycleVariant() },
                    onOpenVariantsMenu = { 
                        showVariantMenu = true
                    },
                    onDateChange = { bodyMapViewModel.setSelectedDate(it) },
                    movingMoleId = movingMoleId,
                    onMoleMoved = { backStackEntry.savedStateHandle.remove<String>("movingMoleId") },
                    onMoleClick = { moleId ->
                        navController.navigate(MoleDetailsRoute(moleId, autoCamera = bodyMapState.rapidUpdateMode))
                    },
                    onAddMole = { x, y, side, color ->
                        val newId = bodyMapViewModel.addMole(x, y, side, color)
                        navController.navigate(MoleDetailsRoute(newId, autoCamera = bodyMapState.rapidInsertionMode))
                    },
                    onUpdatePosition = { id, x, y, side ->
                        bodyMapViewModel.updateMolePosition(id, x, y, side)
                    },
                    onToggleVisibility = { hex ->
                        bodyMapViewModel.toggleColorVisibility(hex)
                    },
                    onFindMoleAt = { internalX, internalY, canvasWidth, canvasHeight, thresholdSq ->
                        bodyMapViewModel.findMoleAtTap(internalX, internalY, canvasWidth, canvasHeight, thresholdSq)
                    },
                    onSnapMoleAt = { x, y, cw, ch, r -> 
                        bodyMapViewModel.snapMolePosition(x, y, cw, ch, r)
                    },
                    onOpenSettings = {
                        navController.navigate(SettingsRoute)
                    }
                )

                if (showVariantMenu) {
                    com.example.chronomapscanner.ui.components.VariantManagementBottomSheet(
                        variants = variants,
                        onDismiss = { showVariantMenu = false },
                        onMove = { from, to -> variantManagementViewModel.updateVariantOrder(from, to) },
                        onUpdateDate = { id, date -> variantManagementViewModel.updateVariantDate(id, date) },
                        onUpdateNotes = { id, notes -> variantManagementViewModel.updateVariantNotes(id, notes) }
                    )
                }
            }

            composable<SettingsRoute> {
                val settingsState by settingsViewModel.settingsUiState.collectAsStateWithLifecycle()
                
                val backgroundSettingsViewModel: BackgroundSettingsViewModel = hiltViewModel()
                val categories by backgroundSettingsViewModel.categoriesFlow.collectAsStateWithLifecycle()
                val activeCategoryId by backgroundSettingsViewModel.activeCategoryId.collectAsStateWithLifecycle()
                val selectedCategoryId by backgroundSettingsViewModel.selectedCategoryId.collectAsStateWithLifecycle()
                val variantsForSelectedCategory by backgroundSettingsViewModel.variantsForSelectedCategory.collectAsStateWithLifecycle()
                val userSettings by backgroundSettingsViewModel.userSettings.collectAsStateWithLifecycle()


                val context = androidx.compose.ui.platform.LocalContext.current
                val savePdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    if (uri != null) {
                        settingsViewModel.generateGlobalReport { file ->
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        file.inputStream().use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "PDF salvato con successo", android.widget.Toast.LENGTH_SHORT).show()
                                        if (settingsState.userSettings.openPdfAutomatically) {
                                            val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            try {
                                                context.startActivity(viewIntent)
                                            } catch (e: android.content.ActivityNotFoundException) {
                                                android.widget.Toast.makeText(context, "Nessuna app trovata per aprire il PDF", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                var pendingExportFilename by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
                val exportFile = pendingExportFilename
                if (exportFile != null) {
                    if (settingsState.userSettings.showExportDialog) {
                        com.example.chronomapscanner.ui.components.ExportDialog(
                            initialQuality = settingsState.userSettings.pdfQuality,
                            initialOpenPdf = settingsState.userSettings.openPdfAutomatically,
                            onDismiss = { pendingExportFilename = null },
                            onConfirm = { quality, openPdf, showDialog ->
                                settingsViewModel.updatePdfQuality(quality)
                                settingsViewModel.updateOpenPdfAutomatically(openPdf)
                                settingsViewModel.updateShowExportDialog(showDialog)
                                savePdfLauncher.launch(exportFile)
                                pendingExportFilename = null
                            }
                        )
                    } else {
                        savePdfLauncher.launch(exportFile)
                        pendingExportFilename = null
                    }
                }

                SettingsScreen(

                    state = settingsState,
                    onBack = { navController.popBackStack() },
                    onUpdateProfileInfo = settingsViewModel::updateProfileInfo,
                    onCreateProfile = settingsViewModel::addProfile,
                    onSaveBodySettings = settingsViewModel::updateBodySettings,
                    onSwitchProfile = settingsViewModel::switchProfile,
                    onDeleteProfile = settingsViewModel::deleteProfile,
                    onExportDatabase = { profile, uri -> settingsViewModel.exportDatabase(profile, uri) { } },
                    onImportDatabaseUri = { settingsViewModel.setPendingImportUri(it) },
                    onPickProfileImageUri = { uri -> 
                        settingsViewModel.saveImageFromUri(uri, "profile_") { path ->
                            settingsViewModel.updateProfileInfo(settingsState.profileName, settingsState.profileName, path)
                        }
                    },
                    onUpdateReminders = settingsViewModel::updateReminderSettings,
                    onUpdateRapidModes = settingsViewModel::updateRapidModes,
                    onUpdateShowZoomButton = settingsViewModel::updateShowZoomButton,
                    onUpdateSmartCameraEnabled = settingsViewModel::updateSmartCameraEnabled,
                    onUpdateScannerSettings = settingsViewModel::updateScannerSettings,
                    onUpdateWarnOnEmptyMoleDeletion = settingsViewModel::updateWarnOnEmptyMoleDeletion,
                    onUpdatePdfQuality = settingsViewModel::updatePdfQuality,
                    onUpdateOpenPdfAutomatically = settingsViewModel::updateOpenPdfAutomatically,
                    onUpdateShowExportDialog = settingsViewModel::updateShowExportDialog,
                    onExportPdf = {
                        settingsViewModel.generateGlobalReport { file ->
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                ".fileprovider",
                                file
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(com.example.chronomapscanner.R.string.share_as_pdf)))
                        }
                    },
                    onSavePdf = {
                        val dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                        val safeProfileName = settingsState.profileName.replace(" ", "_").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                        val fileName = "Report_Globale_${safeProfileName}_${dateStr}.pdf"
                        pendingExportFilename = fileName
                    },
                    onDebugSeed = settingsViewModel::debugResetAndSeed,
                    onTestNotification = settingsViewModel::testNotification,
                    backgroundSettingsContent = {
                        com.example.chronomapscanner.ui.components.BackgroundSettings(
                            categories = categories,
                            activeCategoryId = activeCategoryId,
                            onSetActiveCategory = { id, keepMoles -> backgroundSettingsViewModel.switchCategory(id, keepMoles) },
                            selectedCategoryId = selectedCategoryId,
                            onSelectCategoryForEditing = { backgroundSettingsViewModel.selectCategoryForEditing(it) },
                            variantsForSelectedCategory = variantsForSelectedCategory,
                            onAddVariants = { catId, uris -> backgroundSettingsViewModel.addVariants(catId, uris) },
                            onAddSingleVariant = { catId, name, uri ->
                                backgroundSettingsViewModel.saveTemporaryImageFromUri(uri) { tmpPath ->
                                    if (tmpPath != null) {
                                        backgroundSettingsViewModel.addVariantFromPath(catId, name, tmpPath)
                                        navController.navigate(ImageEditorRoute(tmpPath))
                                    }
                                }
                            },
                            onEditVariantImage = { path ->
                                navController.navigate(ImageEditorRoute(path))
                            },
                            onUpdateVariantName = { variant, name -> backgroundSettingsViewModel.updateVariantName(variant, name) },
                            onUpdateVariantsOrder = { variants -> backgroundSettingsViewModel.updateVariantsOrder(variants) },
                            onDeleteVariant = { backgroundSettingsViewModel.deleteVariant(it) },
                            userSettings = userSettings,
                            onSaveBodySettings = backgroundSettingsViewModel::updateBodySettings
                        )
                    }
                )
            }

            composable<SplitViewRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<SplitViewRoute>()
                val moleId = route.moleId
                val splitViewViewModel = hiltViewModel<SplitViewViewModel, SplitViewViewModel.Factory> { factory ->
                    factory.create(moleId)
                }

                val splitViewState by splitViewViewModel.splitViewUiState.collectAsStateWithLifecycle()
                SplitViewScreen(
                    state = splitViewState,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<MoleDetailsRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<MoleDetailsRoute>()
                val moleId = route.moleId
                val moleDetailsViewModel = hiltViewModel<MoleDetailsViewModel, MoleDetailsViewModel.Factory> { factory ->
                    factory.create(moleId)
                }

                val moleDetailsState by moleDetailsViewModel.moleDetailsUiState.collectAsStateWithLifecycle()
                val warnOnEmptyMoleDeletion by moleDetailsViewModel.warnOnEmptyMoleDeletion.collectAsStateWithLifecycle()

                val pendingPhotoPath by backStackEntry.savedStateHandle.getStateFlow<String?>("pendingPhotoPath", null).collectAsStateWithLifecycle()
                val editingEntryId by backStackEntry.savedStateHandle.getStateFlow<String?>("editingEntryId", null).collectAsStateWithLifecycle()


                val context = androidx.compose.ui.platform.LocalContext.current
                val savePdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    if (uri != null) {
                        moleDetailsViewModel.generateMoleReport(context) { file ->
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        file.inputStream().use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "PDF salvato con successo", android.widget.Toast.LENGTH_SHORT).show()
                                        if (moleDetailsState.userSettings.openPdfAutomatically) {
                                            val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            try {
                                                context.startActivity(viewIntent)
                                            } catch (e: android.content.ActivityNotFoundException) {
                                                android.widget.Toast.makeText(context, "Nessuna app trovata per aprire il PDF", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                var pendingExportFilename by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
                val exportFile = pendingExportFilename
                if (exportFile != null) {
                    if (moleDetailsState.userSettings.showExportDialog) {
                        com.example.chronomapscanner.ui.components.ExportDialog(
                            initialQuality = moleDetailsState.userSettings.pdfQuality,
                            initialOpenPdf = moleDetailsState.userSettings.openPdfAutomatically,
                            onDismiss = { pendingExportFilename = null },
                            onConfirm = { quality, openPdf, showDialog ->
                                settingsViewModel.updatePdfQuality(quality)
                                settingsViewModel.updateOpenPdfAutomatically(openPdf)
                                settingsViewModel.updateShowExportDialog(showDialog)
                                savePdfLauncher.launch(exportFile)
                                pendingExportFilename = null
                            }
                        )
                    } else {
                        savePdfLauncher.launch(exportFile)
                        pendingExportFilename = null
                    }
                }

                MoleDetailsScreen(
                    state = moleDetailsState,
                    warnOnEmptyMoleDeletion = warnOnEmptyMoleDeletion,
                    onSetWarnOnEmptyMoleDeletion = moleDetailsViewModel::setWarnOnEmptyMoleDeletion,
                    pendingPhotoPath = pendingPhotoPath,
                    editingEntryId = editingEntryId,
                    autoCamera = route.autoCamera,
                    onClearPendingPhoto = {
                        backStackEntry.savedStateHandle.remove<String>("pendingPhotoPath")
                        backStackEntry.savedStateHandle.remove<String>("editingEntryId")
                    },
                    onBack = { 
                        moleDetailsViewModel.discardDraftIfEmpty()
                        navController.popBackStack() 
                    },
                    onDeleteMole = {
                        moleDetailsViewModel.deleteMole()
                        navController.popBackStack()
                    },
                    onOpenSplitView = {
                        navController.navigate(SplitViewRoute(moleId))
                    },
                    onAddPhoto = { entryId ->
                        backStackEntry.savedStateHandle["editingEntryId"] = entryId
                        navController.navigate(CameraRoute(moleId))
                    },
                    onPickFromGalleryUri = { entryId, uri ->
                        backStackEntry.savedStateHandle["editingEntryId"] = entryId
                        moleDetailsViewModel.saveImageFromUri(uri, "raw") { savedPath ->
                            if (savedPath != null) {
                                navController.navigate(ImageEditorRoute(savedPath))
                            }
                        }
                    },
                    onReposition = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("movingMoleId", moleId)
                        navController.popBackStack()
                    },
                    onUpdateColor = { color ->
                        moleDetailsViewModel.updateMoleColor(color)
                    },
                    onAddHistoryEntry = { date, notes, image ->
                        moleDetailsViewModel.addHistoryEntry(date, imagePath = image, notes = notes)
                    },
                    onUpdateHistoryEntry = { entryId, date, notes, image ->
                        moleDetailsViewModel.updateHistoryEntry(entryId, date, notes = notes, imagePath = image)
                    },
                    onDeleteHistoryEntry = { entryId ->
                        moleDetailsViewModel.deleteHistoryEntry(entryId)
                    },
                    onSharePdf = {
                        moleDetailsViewModel.generateMoleReport(context) { file ->
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                ".fileprovider",
                                file
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(com.example.chronomapscanner.R.string.share_as_pdf)))
                        }
                    },
                    onSavePdf = {
                        val mole = moleDetailsState.mole
                        if (mole != null) {
                            val sideLower = mole.side.lowercase()
                            val viewName = if (sideLower.contains("front")) context.getString(com.example.chronomapscanner.R.string.front) else if (sideLower.contains("back")) context.getString(com.example.chronomapscanner.R.string.back) else mole.side
                            val dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                            val fileName = "Report_${viewName}_${mole.profileName}_${dateStr}.pdf"
                            pendingExportFilename = fileName
                        } else {
                            pendingExportFilename = "mole_report.pdf"
                        }
                    }
                )
            }

            composable<CameraRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<CameraRoute>()
                val moleId = route.moleId
                val settingsState by settingsViewModel.settingsUiState.collectAsStateWithLifecycle()
                AutoCameraScreen(
                    smartCameraEnabled = settingsState.smartCameraEnabled,
                    onPhotoTaken = { path ->
                        navController.navigate(ImageEditorRoute(path)) {
                            popUpTo<CameraRoute> { inclusive = true }
                        }
                    },
                    onError = { e ->
                        val errorMsg = context.getString(R.string.error_camera, e.localizedMessage)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(errorMsg)
                        }
                        navController.popBackStack()
                    }
                )
            }

            composable<ImageEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ImageEditorRoute>()
                val imagePath = route.imagePath
                val settingsState by settingsViewModel.settingsUiState.collectAsStateWithLifecycle()

                ImageEditorScreen(
                    imagePath = imagePath,
                    onBack = { navController.popBackStack() },
                    onConfirm = { editedPath ->
                        if (imagePath.contains("profile_")) {
                            settingsViewModel.updateProfileInfo(settingsState.profileName, settingsState.profileName, editedPath)
                        } else {
                            navController.previousBackStackEntry?.savedStateHandle?.set("pendingPhotoPath", editedPath)
                        }
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
