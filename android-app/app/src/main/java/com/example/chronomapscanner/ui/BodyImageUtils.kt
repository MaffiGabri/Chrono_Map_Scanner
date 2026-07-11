package com.example.chronomapscanner.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.example.chronomapscanner.R
import com.example.chronomapscanner.data.domain.BodyType
import com.example.chronomapscanner.data.domain.Gender
import com.example.chronomapscanner.data.domain.UserSettings

/**
 * Fix M4: Shared utility to resolve the correct body silhouette drawable
 * based on user settings and the current view side.
 * Previously this logic was duplicated in BodyMapScreen and MoleDetailsScreen.
 */
fun getBodyImageRes(settings: UserSettings, variantId: String, variantName: String? = null): Int {
    val gender = if (settings.gender == Gender.MALE) "male" else "female"
    val type = if (settings.bodyType == BodyType.SLIM) "slim" else "over"
    
    val lowerId = variantId.lowercase()
    val lowerName = variantName?.lowercase() ?: ""
    val sideStr = when {
        lowerId.contains("front") || lowerName.contains("front") || lowerName.contains("fronte") -> "front"
        lowerId.contains("back") || lowerName.contains("back") || lowerName.contains("retro") -> "back"
        else -> "front"
    }

    return when ("${gender}_${type}_${sideStr}") {
        "male_slim_front" -> R.drawable.body_male_slim_front
        "male_slim_back" -> R.drawable.body_male_slim_back
        "male_over_front" -> R.drawable.body_male_over_front
        "male_over_back" -> R.drawable.body_male_over_back
        "female_slim_front" -> R.drawable.body_female_slim_front
        "female_slim_back" -> R.drawable.body_female_slim_back
        "female_over_front" -> R.drawable.body_female_over_front
        "female_over_back" -> R.drawable.body_female_over_back
        else -> R.drawable.body_male_slim_front
    }
}

fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    return try {
        Color(clean.toLong(16) or 0xFF000000)
    } catch(e: Exception) {
        Color.Transparent
    }
}

fun calculateMolePosition(
    tapOffset: Offset,
    currentOffset: Offset,
    currentScale: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    fittedWidth: Float,
    fittedHeight: Float
): Pair<Float, Float> {
    val relX = (tapOffset.x - canvasWidth / 2f - currentOffset.x) / (fittedWidth * currentScale) + 0.5f
    val relY = (tapOffset.y - canvasHeight / 2f - currentOffset.y) / (fittedHeight * currentScale) + 0.5f
    val x = (relX * 100f).coerceIn(0f, 100f)
    val y = (relY * 100f).coerceIn(0f, 100f)
    return Pair(x, y)
}
