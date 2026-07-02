package com.example.skinhistoryscanner.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.example.skinhistoryscanner.R
import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.domain.UserSettings

/**
 * Fix M4: Shared utility to resolve the correct body silhouette drawable
 * based on user settings and the current view side.
 * Previously this logic was duplicated in BodyMapScreen and MoleDetailsScreen.
 */
fun getBodyImageRes(settings: UserSettings, variantId: String): Int {
    val gender = if (settings.gender == Gender.MALE) "male" else "female"
    val type = if (settings.bodyType == BodyType.SLIM) "slim" else "over"
    
    val lowerId = variantId.lowercase()
    val sideStr = when {
        lowerId.contains("front") -> "front"
        lowerId.contains("back") -> "back"
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
    width: Int,
    height: Int
): Pair<Float, Float> {
    val relX = (tapOffset.x - currentOffset.x - width / 2f) / (width * currentScale) + 0.5f
    val relY = (tapOffset.y - currentOffset.y - height / 2f) / (height * currentScale) + 0.5f
    val x = (relX * 100f).coerceIn(0f, 100f)
    val y = (relY * 100f).coerceIn(0f, 100f)
    return Pair(x, y)
}
