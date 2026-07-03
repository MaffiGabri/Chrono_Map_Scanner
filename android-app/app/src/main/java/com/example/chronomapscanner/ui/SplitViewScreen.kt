package com.example.chronomapscanner.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.chronomapscanner.data.domain.HistoryEntry
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitViewScreen(
    state: SplitViewUiState,
    onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val photoHistory = state.photoHistory
    var leftIndex by remember { mutableIntStateOf(0) }
    var rightIndex by remember { mutableIntStateOf(photoHistory.size - 1) }

    // Update indices if history changes (e.g. initial load)
    LaunchedEffect(photoHistory) {
        if (photoHistory.isNotEmpty()) {
            leftIndex = 0
            rightIndex = photoHistory.size - 1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.split_view_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (photoHistory.size < 2) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.split_view_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val leftPhoto = photoHistory.getOrNull(leftIndex)
            val rightPhoto = photoHistory.getOrNull(rightIndex)

            if (isLandscape) {
                Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ZoomableImage(leftPhoto?.imagePath)
                        ImageNavigator(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            currentDate = leftPhoto?.date,
                            onPrev = { if (leftIndex > 0) leftIndex-- },
                            onNext = { if (leftIndex < photoHistory.size - 1) leftIndex++ },
                            hasPrev = leftIndex > 0,
                            hasNext = leftIndex < photoHistory.size - 1
                        )
                    }
                    VerticalDivider()
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ZoomableImage(rightPhoto?.imagePath)
                        ImageNavigator(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            currentDate = rightPhoto?.date,
                            onPrev = { if (rightIndex > 0) rightIndex-- },
                            onNext = { if (rightIndex < photoHistory.size - 1) rightIndex++ },
                            hasPrev = rightIndex > 0,
                            hasNext = rightIndex < photoHistory.size - 1
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ZoomableImage(leftPhoto?.imagePath)
                        ImageNavigator(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            currentDate = leftPhoto?.date,
                            onPrev = { if (leftIndex > 0) leftIndex-- },
                            onNext = { if (leftIndex < photoHistory.size - 1) leftIndex++ },
                            hasPrev = leftIndex > 0,
                            hasNext = leftIndex < photoHistory.size - 1
                        )
                    }
                    HorizontalDivider()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ZoomableImage(rightPhoto?.imagePath)
                        ImageNavigator(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            currentDate = rightPhoto?.date,
                            onPrev = { if (rightIndex > 0) rightIndex-- },
                            onNext = { if (rightIndex < photoHistory.size - 1) rightIndex++ },
                            hasPrev = rightIndex > 0,
                            hasNext = rightIndex < photoHistory.size - 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomableImage(imagePath: String?) {
    if (imagePath == null) return
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(imagePath))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun ImageNavigator(
    modifier: Modifier = Modifier,
    currentDate: LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    hasPrev: Boolean,
    hasNext: Boolean
) {
    val localDate = currentDate ?: LocalDate.now()
    val daysAgo = ChronoUnit.DAYS.between(localDate, LocalDate.now())
    val relativeText = when {
        daysAgo == 0L -> androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.today)
        daysAgo == 1L -> androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.yesterday)
        daysAgo < 7L -> androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.days_ago, daysAgo)
        daysAgo < 30L -> androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.weeks_ago, daysAgo / 7)
        else -> androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.months_ago, daysAgo / 30)
    }

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onPrev, enabled = hasPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.previous_photo), tint = if (hasPrev) Color.White else Color.Gray)
            }
            Text(
                text = "${localDate.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault()))} ($relativeText)",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = androidx.compose.ui.res.stringResource(com.example.chronomapscanner.R.string.next_photo), tint = if (hasNext) Color.White else Color.Gray)
            }
        }
    }
}
