package com.example.skinhistoryscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.skinhistoryscanner.ui.PreviewSize
import com.example.skinhistoryscanner.utils.getLocalizedColorLabel
import java.io.File
import kotlin.math.roundToInt

@Composable
fun MoleMarker(
    id: String,
    color: Color,
    thumbnail: String?,
    absoluteX: Float,
    absoluteY: Float,
    previewSize: PreviewSize,
    isMoving: Boolean
) {
    val currentMarkerSize = when (previewSize) {
        PreviewSize.IMAGE_LARGE -> 48.dp
        PreviewSize.IMAGE_SMALL -> 24.dp
        PreviewSize.COLORED_DOT -> 16.dp
    }
    
    val density = LocalDensity.current
    val sizePx = with(density) { currentMarkerSize.toPx() }
    
    val finalX = absoluteX - (sizePx / 2f)
    val finalY = absoluteY - (sizePx / 2f)

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(finalX.roundToInt(), finalY.roundToInt()) }
                .size(currentMarkerSize)
                .clip(CircleShape)
                .background(if (isMoving) color.copy(alpha = 0.5f) else color)
                .border(2.dp, color, CircleShape)
                .padding(1.dp)
                .border(1.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (previewSize != PreviewSize.COLORED_DOT && thumbnail != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(thumbnail))
                        .crossfade(true)
                        .size(150)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
