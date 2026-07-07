package com.example.chronomapscanner.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

sealed class ImageEditorState {
    object Loading : ImageEditorState()
    data class Ready(val bitmap: Bitmap) : ImageEditorState()
    data class Error(val message: String) : ImageEditorState()
    data class Saving(val progress: Float = 0f) : ImageEditorState()
    data class Saved(val path: String) : ImageEditorState()
}

@HiltViewModel
class ImageEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageEditorState>(ImageEditorState.Loading)
    val uiState: StateFlow<ImageEditorState> = _uiState.asStateFlow()

    private var currentBitmap: Bitmap? = null

    fun loadImage(imagePath: String) {
        viewModelScope.launch {
            _uiState.value = ImageEditorState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = decodeSampledBitmapFromFile(imagePath, 1920, 1080)
                    if (bitmap != null) {
                        setNewBitmap(bitmap)
                    } else {
                        _uiState.value = ImageEditorState.Error("Failed to decode image")
                    }
                } catch (e: Exception) {
                    _uiState.value = ImageEditorState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun rotateImage() {
        val bitmap = currentBitmap ?: return
        viewModelScope.launch {
            _uiState.value = ImageEditorState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val matrix = Matrix().apply { postRotate(90f) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    setNewBitmap(rotated)
                } catch (e: Exception) {
                    _uiState.value = ImageEditorState.Error(e.message ?: "Error rotating image")
                    setNewBitmap(bitmap) // Restore
                }
            }
        }
    }

    fun cropAndSaveImage(
        cropX: Int, cropY: Int, cropW: Int, cropH: Int
    ) {
        val bitmap = currentBitmap ?: return
        viewModelScope.launch {
            _uiState.value = ImageEditorState.Saving()
            withContext(Dispatchers.IO) {
                try {
                    val safeX = cropX.coerceIn(0, bitmap.width - 1)
                    val safeY = cropY.coerceIn(0, bitmap.height - 1)
                    val safeW = cropW.coerceAtMost(bitmap.width - safeX).coerceAtLeast(1)
                    val safeH = cropH.coerceAtMost(bitmap.height - safeY).coerceAtLeast(1)

                    val cropped = Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
                    val finalFile = File(context.filesDir, "edited_${UUID.randomUUID()}.jpg")

                    FileOutputStream(finalFile).use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    // Generate thumbnail
                    val thumbFile = File(context.filesDir, "thumb_${finalFile.name}")
                    val aspect = cropped.height.toFloat() / cropped.width
                    val reqSize = 150
                    val thumbW = if (aspect > 1f) (reqSize / aspect).toInt() else reqSize
                    val thumbH = if (aspect > 1f) reqSize else (reqSize * aspect).toInt()
                    val thumbBitmap = Bitmap.createScaledBitmap(cropped, thumbW.coerceAtLeast(1), thumbH.coerceAtLeast(1), true)
                    FileOutputStream(thumbFile).use { out ->
                        thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    thumbBitmap.recycle()
                    
                    cropped.recycle() // Recycle temporary cropped bitmap

                    withContext(Dispatchers.Main) {
                        _uiState.value = ImageEditorState.Saved(finalFile.absolutePath)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = ImageEditorState.Error(e.message ?: "Failed to save image")
                        setNewBitmap(bitmap) // Restore
                    }
                }
            }
        }
    }

    private fun setNewBitmap(newBitmap: Bitmap) {
        val oldBitmap = currentBitmap
        currentBitmap = newBitmap
        _uiState.value = ImageEditorState.Ready(newBitmap)
        if (oldBitmap != null && oldBitmap != newBitmap) {
            oldBitmap.recycle()
        }
    }

    fun resetState() {
        val bitmap = currentBitmap
        if (bitmap != null) {
            _uiState.value = ImageEditorState.Ready(bitmap)
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentBitmap?.recycle()
        currentBitmap = null
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, this)
            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
            inJustDecodeBounds = false
        }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null

        try {
            val exif = androidx.exifinterface.media.ExifInterface(path)
            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED
            )
            
            val matrix = Matrix()
            when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            return rotatedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return bitmap
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
