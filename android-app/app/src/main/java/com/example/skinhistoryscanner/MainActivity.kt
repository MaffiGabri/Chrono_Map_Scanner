package com.example.skinhistoryscanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.skinhistoryscanner.ui.viewmodels.SettingsViewModel
import com.example.skinhistoryscanner.ui.*
import com.example.skinhistoryscanner.ui.navigation.*
import com.example.skinhistoryscanner.theme.SkinHistoryScannerTheme
import com.example.skinhistoryscanner.notifications.ReminderManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import dagger.hilt.android.AndroidEntryPoint

import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri

import com.example.skinhistoryscanner.utils.Seeder
import com.example.skinhistoryscanner.data.local.room.AppDatabaseRoom
import javax.inject.Inject

@AndroidEntryPoint
// Invalidate KSP cache
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var database: AppDatabaseRoom

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SkinHistoryScannerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SkinHistoryScannerApp()
                }
            }
        }
    }
}

@Composable
fun SkinHistoryScannerApp(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    var showImportDialog by remember { mutableStateOf(false) }
    val importUri by settingsViewModel.pendingImportUri.collectAsStateWithLifecycle()
    val processing by settingsViewModel.isProcessing.collectAsStateWithLifecycle()

    if (importUri != null) {
        LaunchedEffect(importUri) {
            showImportDialog = true
        }
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        SkinHistoryNavGraph(
            navController = navController,
            settingsViewModel = settingsViewModel,
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope
        )
    }
    
    if (showImportDialog && importUri != null) {
        com.example.skinhistoryscanner.ui.components.ImportActionDialog(
            onOverwrite = {
                settingsViewModel.importDatabase(importUri!!, SettingsViewModel.ImportMode.OVERWRITE) {
                    showImportDialog = false
                    settingsViewModel.setPendingImportUri(null)
                }
            },
            onNewProfile = { name ->
                settingsViewModel.importDatabase(importUri!!, SettingsViewModel.ImportMode.NEW_PROFILE, name) {
                    showImportDialog = false
                    settingsViewModel.setPendingImportUri(null)
                }
            },
            onCancel = { 
                showImportDialog = false 
                settingsViewModel.setPendingImportUri(null)
            }
        )
    }

    if (processing) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.processing_data), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
    }
}
