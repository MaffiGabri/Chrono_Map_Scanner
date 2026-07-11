package com.example.chronomapscanner.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object BodyMapRoute

@Serializable
object SettingsRoute

@Serializable
data class MoleDetailsRoute(val moleId: String, val autoCamera: Boolean = false)

@Serializable
data class ImageEditorRoute(val imagePath: String)

@Serializable
data class SplitViewRoute(val moleId: String)

@Serializable
data class CameraRoute(val moleId: String)
