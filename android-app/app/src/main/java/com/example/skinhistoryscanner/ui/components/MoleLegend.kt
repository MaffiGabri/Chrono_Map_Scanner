package com.example.skinhistoryscanner.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.skinhistoryscanner.R
import com.example.skinhistoryscanner.data.domain.ColorSetting
import com.example.skinhistoryscanner.utils.getLocalizedColorLabel

@Composable
fun MoleLegend(
    isVisible: Boolean,
    colorSettings: List<ColorSetting>,
    moleCounts: Map<String, Int>,
    onToggleVisibility: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.width(240.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.categories), 
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(16.dp))
                    }
                }
                colorSettings.forEach { setting ->
                    val count = moleCounts[setting.hex] ?: 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (setting.visible) Color.Transparent 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .pointerInput(setting.hex) {
                                detectTapGestures { onToggleVisibility(setting.hex) }
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    try {
                                        Color(android.graphics.Color.parseColor(setting.hex))
                                    } catch (e: Exception) {
                                        Color.Gray
                                    }
                                )
                        )
                        Text(
                            text = getLocalizedColorLabel(setting.label),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(start = 8.dp, end = 4.dp),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = if (setting.visible) {
                                MaterialTheme.colorScheme.onSurface 
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            }
                        )
                        Text(
                            text = "($count)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.wrapContentWidth().padding(end = 8.dp),
                            maxLines = 1,
                            color = if (setting.visible) {
                                MaterialTheme.colorScheme.onSurface 
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            }
        }
    }
}
