package com.example.chronomapscanner.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import kotlin.math.roundToInt

@Composable
fun TimelineSlider(
    isVisible: Boolean,
    selectedDate: LocalDate,
    availableDates: List<LocalDate>,
    onDateChange: (LocalDate) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val safeDates = if (availableDates.isEmpty()) listOf(LocalDate.now()) else availableDates
    val totalSteps = (safeDates.size - 1).toFloat()
    
    val currentIndex = remember(selectedDate, safeDates) {
        safeDates.indexOf(selectedDate).takeIf { it >= 0 } ?: run {
            var closestIdx = 0
            var minDiff = Long.MAX_VALUE
            safeDates.forEachIndexed { idx, d ->
                val diff = kotlin.math.abs(ChronoUnit.DAYS.between(d, selectedDate))
                if (diff < minDiff) {
                    minDiff = diff
                    closestIdx = idx
                }
            }
            closestIdx
        }
    }
    
    var isDragging by remember { mutableStateOf(false) }
    var localSliderValue by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }
    val sliderValue = if (isDragging) localSliderValue else currentIndex.toFloat()

    val sliderContent: @Composable () -> Unit = {
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                localSliderValue = it
                val idx = it.roundToInt().coerceIn(0, safeDates.size - 1)
                onDateChange(safeDates[idx])
            },
            onValueChangeFinished = {
                isDragging = false
            },
            valueRange = 0f..maxOf(0f, totalSteps),
            steps = maxOf(0, safeDates.size - 2),
            modifier = Modifier.fillMaxWidth()
        )
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = if (isCompact) fadeIn() + expandHorizontally() else slideInVertically { -it } + fadeIn(),
        exit = if (isCompact) fadeOut() + shrinkHorizontally() else slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        if (isCompact) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                sliderContent()
            }
        } else {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    sliderContent()
                }
            }
        }
    }
}
