package com.example.chronomapscanner.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chronomapscanner.ui.components.MoleLegend
import com.example.chronomapscanner.ui.components.MoleMarker
import com.example.chronomapscanner.ui.components.TimelineSlider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.example.chronomapscanner.R
import com.example.chronomapscanner.data.domain.BodySide
import com.example.chronomapscanner.utils.getLocalizedColorLabel
import kotlinx.coroutines.launch

enum class PreviewSize {
    IMAGE_SMALL, IMAGE_LARGE, COLORED_DOT
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMapScreen(
    state: BodyMapUiState,
    onCycleVariant: () -> Unit,
    onOpenVariantsMenu: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    movingMoleId: String? = null,
    onMoleMoved: () -> Unit = {},
    onMoleClick: (String) -> Unit,
    onAddMole: (Float, Float, String, String) -> Unit,
    onUpdatePosition: (String, Float, Float, String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onFindMoleAt: suspend (Float, Float, Float, Float, Float) -> String?,
    onSnapMoleAt: suspend (Float, Float, Float, Float, Float) -> Pair<Float, Float>,
    onOpenSettings: () -> Unit,
    getThumbnail: (String) -> androidx.compose.ui.graphics.ImageBitmap? = { null }
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scaleState = remember { mutableFloatStateOf(1f) }
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()
    
    var isSliderVisible by rememberSaveable { mutableStateOf(false) }
    var showLegend by rememberSaveable { mutableStateOf(false) }

    val dismissPanels = {
        if (!state.keepLegendVisible) {
            isSliderVisible = false
            showLegend = false
        }
    }

    var previewSize by remember { mutableStateOf(PreviewSize.IMAGE_SMALL) }
    var isAddingMole by remember { mutableStateOf(false) }
    
    // Color picker for new mole
    var showNewMoleColorPicker by remember { mutableStateOf(false) }
    var pendingMoleCoords by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        onDateChange(newDate)
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

    // Thumbnails are now directly in MoleUiModel

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = state.profileName.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                        TextButton(
                            onClick = {
                                if (!isSliderVisible) isSliderVisible = true else showDatePicker = true
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = state.selectedDate.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault())),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Icon(
                                if (isSliderVisible) Icons.Default.CalendarToday else Icons.Default.History,
                                contentDescription = stringResource(R.string.time_machine),
                                modifier = Modifier.size(24.dp).padding(start = 8.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isSliderVisible = !isSliderVisible }) {
                        Icon(
                            if (isSliderVisible) Icons.Default.Close else Icons.Default.History,
                            contentDescription = stringResource(R.string.time_machine)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = { isAddingMole = !isAddingMole },
                    expanded = isAddingMole,
                    icon = { Icon(if (isAddingMole) Icons.Default.Close else Icons.Default.Add, contentDescription = stringResource(if (isAddingMole) R.string.desc_cancel_add else R.string.desc_add_mole)) },
                    text = { Text(stringResource(if (isAddingMole) R.string.cancel else R.string.add)) },
                    containerColor = if (isAddingMole) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (state.showZoomButton) {
                    FloatingActionButton(
                        onClick = {
                            if (scaleState.floatValue < 3f) {
                                scaleState.floatValue = 3f
                            } else if (scaleState.floatValue < 5f) {
                                scaleState.floatValue = 5f
                            } else {
                                scaleState.floatValue = 1f
                                offsetState.value = Offset.Zero
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(bottom = 16.dp).size(48.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.show_zoom_button))
                    }
                }

                FloatingActionButton(
                    onClick = { showLegend = !showLegend },
                    containerColor = if (showLegend) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(R.string.legend_title))
                }
            }
        }
    ) { padding ->
        val maxDate = state.availableDates.maxOrNull()
        val isPastDate = maxDate != null && state.selectedDate < maxDate
        val focusModeColor = Color.Black.copy(alpha = 0.5f)
        val normalColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        val pastColor = Color(0xFFF4ECE1).copy(alpha = 0.8f) // Sepia/Old time tint

        val isFocusMode = isAddingMole || movingMoleId != null
        val backgroundColor by animateColorAsState(
            targetValue = when {
                isFocusMode -> focusModeColor
                isPastDate -> pastColor
                else -> normalColor
            },
            label = "bg_color_anim"
        )
        
        val currentMoles by rememberUpdatedState(state.moles)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape)
                    .pointerInput(movingMoleId, isAddingMole) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scaleState.floatValue < 5f) {
                                    scaleState.floatValue = 5f
                                } else {
                                    scaleState.floatValue = 1f
                                    offsetState.value = Offset.Zero
                                }
                            },
                            onTap = { tapOffset ->
                                    val markerRadiusDp = when (previewSize) {
                                        PreviewSize.IMAGE_LARGE -> 24f
                                        PreviewSize.IMAGE_SMALL -> 12f
                                        PreviewSize.COLORED_DOT -> 8f
                                    }
                                    val touchRadiusDp = kotlin.math.max(markerRadiusDp + 4f, 24f)
                                    val threshold = touchRadiusDp * density.density
                                    
                                    if (isAddingMole || movingMoleId != null) {
                                        coroutineScope.launch {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val (rawX, rawY) = calculateMolePosition(
                                                tapOffset = tapOffset,
                                                currentOffset = offsetState.value,
                                                currentScale = scaleState.floatValue,
                                                width = size.width,
                                                height = size.height
                                            )
                                            
                                            val visualRadiusPx = markerRadiusDp * density.density
                                            val (snappedX, snappedY) = onSnapMoleAt(rawX, rawY, size.width.toFloat(), size.height.toFloat(), visualRadiusPx)
                                            
                                            if (isAddingMole) {
                                                pendingMoleCoords = Pair(snappedX, snappedY)
                                                showNewMoleColorPicker = true
                                            } else if (movingMoleId != null) {
                                                onUpdatePosition(movingMoleId, snappedX, snappedY, state.currentVariant?.id ?: "")
                                                onMoleMoved()
                                            }
                                        }
                                    } else {
                                        val internalX = (tapOffset.x - size.width / 2f - offsetState.value.x) / scaleState.floatValue + size.width / 2f
                                        val internalY = (tapOffset.y - size.height / 2f - offsetState.value.y) / scaleState.floatValue + size.height / 2f

                                        coroutineScope.launch {
                                            val clickedMoleId = onFindMoleAt(internalX, internalY, size.width.toFloat(), size.height.toFloat(), threshold * threshold)
                                        
                                        if (clickedMoleId != null) {
                                            onMoleClick(clickedMoleId)
                                        } else {
                                            dismissPanels()
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scaleState.floatValue = (scaleState.floatValue * zoom).coerceIn(1f, 5f)
                            if (scaleState.floatValue == 1f) {
                                offsetState.value = Offset.Zero
                            } else {
                                val maxX = (scaleState.floatValue - 1) * size.width / 2f
                                val maxY = (scaleState.floatValue - 1) * size.height / 2f
                                val newOffset = offsetState.value + pan
                                offsetState.value = Offset(
                                    x = newOffset.x.coerceIn(-maxX, maxX),
                                    y = newOffset.y.coerceIn(-maxY, maxY)
                                )
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scaleState.floatValue
                        scaleY = scaleState.floatValue
                        translationX = offsetState.value.x
                        translationY = offsetState.value.y
                    }
            ) {
                AnimatedContent(
                    targetState = state.currentVariant,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f))
                            .togetherWith(fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.95f))
                    },
                    label = "body_flip"
                ) { targetVariant ->
                    if (targetVariant != null) {
                        if (targetVariant.isBuiltIn) {
                            val bodyImageRes = remember(state.userSettings, targetVariant.id) {
                                getBodyImageRes(state.userSettings, targetVariant.id)
                            }
                            val bodyPainter = painterResource(id = bodyImageRes)
                            
                            Image(
                                painter = bodyPainter,
                                contentDescription = stringResource(R.string.body_map_image),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            AsyncImage(
                                model = targetVariant.imagePath,
                                contentDescription = stringResource(R.string.body_map_image),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = with(density) { maxWidth.toPx() }
                    val canvasHeight = with(density) { maxHeight.toPx() }
                    val sharedPath = remember { androidx.compose.ui.graphics.Path() }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseRadius = when (previewSize) {
                            PreviewSize.IMAGE_LARGE -> 24.dp.toPx()
                            PreviewSize.IMAGE_SMALL -> 12.dp.toPx()
                            PreviewSize.COLORED_DOT -> 8.dp.toPx()
                        }
                        
                        for (mole in state.moles) {
                            if (mole.id != movingMoleId) {
                                val relX = mole.x / 100f
                                val relY = mole.y / 100f
                                val posX = (relX - 0.5f) * canvasWidth + (canvasWidth / 2f)
                                val posY = (relY - 0.5f) * canvasHeight + (canvasHeight / 2f)
                                
                                val hexColor = mole.color
                                
                                if (previewSize != PreviewSize.COLORED_DOT && mole.latestPhotoPath != null) {
                                    val bitmap = getThumbnail(mole.latestPhotoPath)
                                    if (bitmap != null) {
                                        val dstSize = androidx.compose.ui.unit.IntSize((baseRadius * 2).toInt(), (baseRadius * 2).toInt())
                                        val dstOffset = androidx.compose.ui.unit.IntOffset((posX - baseRadius).toInt(), (posY - baseRadius).toInt())
                                        sharedPath.reset()
                                        sharedPath.addOval(androidx.compose.ui.geometry.Rect(
                                            left = posX - baseRadius,
                                            top = posY - baseRadius,
                                            right = posX + baseRadius,
                                            bottom = posY + baseRadius
                                        ))
                                        clipPath(sharedPath) {
                                            drawImage(
                                                image = bitmap,
                                                dstOffset = dstOffset,
                                                dstSize = dstSize
                                            )
                                        }
                                        drawCircle(
                                            color = hexColor,
                                            radius = baseRadius,
                                            center = Offset(posX, posY),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                        )
                                    } else {
                                        drawCircle(
                                            color = Color.LightGray,
                                            radius = baseRadius,
                                            center = Offset(posX, posY)
                                        )
                                    }
                                } else {
                                    drawCircle(
                                        color = hexColor,
                                        radius = baseRadius,
                                        center = Offset(posX, posY)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = baseRadius,
                                        center = Offset(posX, posY),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }

                        val movingMole = state.moles.find { it.id == movingMoleId }
                        if (movingMole != null) {
                            val relX = movingMole.x / 100f
                            val relY = movingMole.y / 100f
                            val posX = (relX - 0.5f) * canvasWidth + (canvasWidth / 2f)
                            val posY = (relY - 0.5f) * canvasHeight + (canvasHeight / 2f)
                            val movingRadius = baseRadius * 1.5f
                            drawCircle(
                                color = movingMole.color,
                                radius = movingRadius,
                                center = Offset(posX, posY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = movingRadius,
                                center = Offset(posX, posY),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }
            }

            TimelineSlider(
                isVisible = isSliderVisible,
                selectedDate = state.selectedDate,
                availableDates = state.availableDates,
                onDateChange = onDateChange,
                onClose = { isSliderVisible = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.78f) // Slightly longer to get closer to buttons without overlapping
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                if (state.variants.size > 1) {
                    SmallFloatingActionButton(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCycleVariant() 
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onCycleVariant()
                                    },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenVariantsMenu()
                                    }
                                )
                            },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        val icon = if (state.variants.size > 2) Icons.Default.ViewCarousel else Icons.Default.Flip
                        Icon(icon, contentDescription = stringResource(R.string.flip_view))
                    }
                }

                SmallFloatingActionButton(
                    onClick = { 
                        previewSize = when(previewSize) {
                            PreviewSize.IMAGE_SMALL -> PreviewSize.IMAGE_LARGE
                            PreviewSize.IMAGE_LARGE -> PreviewSize.COLORED_DOT
                            PreviewSize.COLORED_DOT -> PreviewSize.IMAGE_SMALL
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Icon(
                        when(previewSize) {
                            PreviewSize.IMAGE_SMALL -> Icons.Default.PhotoSizeSelectSmall
                            PreviewSize.IMAGE_LARGE -> Icons.Default.PhotoSizeSelectActual
                            PreviewSize.COLORED_DOT -> Icons.Default.Lens
                        },
                        contentDescription = stringResource(R.string.marker_size)
                    )
                }
            }

            MoleLegend(
                isVisible = showLegend,
                colorSettings = state.colorSettings,
                moleCounts = state.moleCountsByColor,
                onToggleVisibility = onToggleVisibility,
                onClose = { showLegend = false },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
            )

            if (state.isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }

            if (isAddingMole || movingMoleId != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                ) {
                    val labelText = if (isAddingMole) stringResource(R.string.add_hint) else stringResource(R.string.reposition_hint)
                    Text(labelText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showNewMoleColorPicker && pendingMoleCoords != null) {
        ColorPickerOverlay(
            colorSettings = state.colorSettings,
            onColorSelected = { colorHex ->
                onAddMole(pendingMoleCoords!!.first, pendingMoleCoords!!.second, state.currentVariant?.id ?: "", colorHex)
                showNewMoleColorPicker = false
                isAddingMole = false
                pendingMoleCoords = null
            },
            onDismiss = {
                showNewMoleColorPicker = false
                pendingMoleCoords = null
            }
        )
    }
}

@Composable
fun ColorPickerOverlay(
    colorSettings: List<com.example.chronomapscanner.data.domain.ColorSetting>,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories)) },
        text = {
            Column {
                colorSettings.forEach { setting ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .pointerInput(setting.hex) {
                                detectTapGestures { onColorSelected(setting.hex) }
                            }
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(setting.hex))
                        )
                        Text(
                            text = getLocalizedColorLabel(setting.label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
