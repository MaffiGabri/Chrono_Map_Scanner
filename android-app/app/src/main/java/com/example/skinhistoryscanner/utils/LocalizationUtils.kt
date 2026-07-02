package com.example.skinhistoryscanner.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.skinhistoryscanner.R

@Composable
fun getLocalizedColorLabel(colorKey: String): String {
    return when (colorKey) {
        "color_alarm" -> stringResource(R.string.color_alarm)
        "color_suspicious" -> stringResource(R.string.color_suspicious)
        "color_monitor" -> stringResource(R.string.color_monitor)
        "color_safe" -> stringResource(R.string.color_safe)
        "color_new" -> stringResource(R.string.color_new)
        "color_other" -> stringResource(R.string.color_other)
        else -> colorKey // Fallback to key if not matched (legacy data)
    }
}
