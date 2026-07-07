package com.example.chronomapscanner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chronomapscanner.ui.viewmodels.ImageEditorState
import com.example.chronomapscanner.ui.viewmodels.ImageEditorViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.chronomapscanner.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    imagePath: String,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
    viewModel: ImageEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(imagePath) {
        viewModel.loadImage(imagePath)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // Fix A3: Intercept system back button to prevent accidental loss
    BackHandler { showExitConfirm = true }

    val cropSize = remember(canvasSize) {
        if (canvasSize == IntSize.Zero) 0f 
        else (minOf(canvasSize.width, canvasSize.height) * 0.85f)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.labelLarge, color = Color.White, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.rotateImage()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = stringResource(R.string.editor_rotate), tint = Color.White)
                    }
                    IconButton(onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.editor_reset), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars), // Prevent overlap with system nav bar
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (uiState is ImageEditorState.Ready) {
                                val bitmap = (uiState as ImageEditorState.Ready).bitmap
                                val centerX = canvasSize.width / 2f
                                val centerY = canvasSize.height / 2f
                                val cropRectOnCanvas = Rect(centerX - cropSize / 2, centerY - cropSize / 2, centerX + cropSize / 2, centerY + cropSize / 2)
                                val initialScale = minOf(canvasSize.width.toFloat() / bitmap.width, canvasSize.height.toFloat() / bitmap.height)
                                val totalScale = initialScale * scale
                                val initialOffsetX = (canvasSize.width - bitmap.width * initialScale) / 2f
                                val initialOffsetY = (canvasSize.height - bitmap.height * initialScale) / 2f
                                val cropInBitmapX = (cropRectOnCanvas.left - (initialOffsetX + offset.x - (bitmap.width * totalScale - bitmap.width * initialScale)/2f)) / totalScale
                                val cropInBitmapY = (cropRectOnCanvas.top - (initialOffsetY + offset.y - (bitmap.height * totalScale - bitmap.height * initialScale)/2f)) / totalScale
                                val cropInBitmapSize = cropSize / totalScale

                                viewModel.cropAndSaveImage(
                                    cropInBitmapX.toInt(),
                                    cropInBitmapY.toInt(),
                                    cropInBitmapSize.toInt(),
                                    cropInBitmapSize.toInt()
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.editor_confirm), modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    ) { padding ->
        LaunchedEffect(uiState) {
            when (val state = uiState) {
                is ImageEditorState.Saved -> onConfirm(state.path)
                is ImageEditorState.Error -> {
                    android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.resetState()
                }
                else -> {}
            }
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset += pan
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (uiState is ImageEditorState.Ready) {
                    val bitmap = (uiState as ImageEditorState.Ready).bitmap
                    val initialScale = minOf(size.width / bitmap.width, size.height / bitmap.height)
                    val drawW = bitmap.width * initialScale
                    val drawH = bitmap.height * initialScale
                    val center = Offset(size.width / 2f, size.height / 2f)
                    
                    withTransform({
                        translate(offset.x, offset.y)
                        scale(scale, scale, pivot = center)
                    }) {
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = IntOffset(((size.width - drawW) / 2f).toInt(), ((size.height - drawH) / 2f).toInt()),
                            dstSize = IntSize(drawW.toInt(), drawH.toInt())
                        )
                    }
                }

                val cropLeft = (size.width - cropSize) / 2f
                val cropTop = (size.height - cropSize) / 2f
                
                // Darken outside
                drawRect(Color.Black.copy(alpha = 0.7f), topLeft = Offset.Zero, size = Size(size.width, cropTop))
                drawRect(Color.Black.copy(alpha = 0.7f), topLeft = Offset(0f, cropTop + cropSize), size = Size(size.width, size.height - (cropTop + cropSize)))
                drawRect(Color.Black.copy(alpha = 0.7f), topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropSize))
                drawRect(Color.Black.copy(alpha = 0.7f), topLeft = Offset(cropLeft + cropSize, cropTop), size = Size(size.width - (cropLeft + cropSize), cropSize))
                
                // Guides
                val guideWidth = 1.dp.toPx()
                val third = cropSize / 3f
                // Verticals
                drawRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(cropLeft + third, cropTop), size = Size(guideWidth, cropSize))
                drawRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(cropLeft + 2 * third, cropTop), size = Size(guideWidth, cropSize))
                // Horizontals
                drawRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(cropLeft, cropTop + third), size = Size(cropSize, guideWidth))
                drawRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(cropLeft, cropTop + 2 * third), size = Size(cropSize, guideWidth))

                // Border
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropLeft, cropTop),
                    size = Size(cropSize, cropSize),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                if (uiState is ImageEditorState.Loading || uiState is ImageEditorState.Saving) {
                    drawRect(Color.Black.copy(alpha = 0.5f))
                }
            }
            if (uiState is ImageEditorState.Loading || uiState is ImageEditorState.Saving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
        }
    }

    // Fix A3: Exit confirmation dialog
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.editor_exit_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.editor_exit_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.editor_exit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text(stringResource(R.string.editor_exit_cancel)) }
            }
        )
    }
}
