package com.example.skinhistoryscanner.ui

import androidx.compose.ui.graphics.Color

data class MoleUiModel(
    val id: String,
    val x: Float,
    val y: Float,
    val colorHex: String,
    val color: Color,
    val side: String,
    val latestPhotoPath: String?
)
