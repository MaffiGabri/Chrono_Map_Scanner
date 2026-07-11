package com.example.chronomapscanner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chronomapscanner.R
import com.example.chronomapscanner.utils.AlgorithmicMoleDetector
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger


data class MoleAnalysisResult(
    val isSkinDetected: Boolean,
    val isMoleDetected: Boolean,
    val isTooClose: Boolean
)

@OptIn(ExperimentalGetImage::class)
@Composable
fun AutoCameraScreen(
    smartCameraEnabled: Boolean,
    onPhotoTaken: (String) -> Unit,
    onError: (Exception) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.camera_permission_required))
        }
        return
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isStable by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    var isMoleDetected by remember { mutableStateOf(false) }
    var autoCaptureProgress by remember { mutableStateOf(0f) }
    var isCapturing by remember { mutableStateOf(false) }
    
    var isLongPressing by remember { mutableStateOf(false) }
    val manualCaptureProgress by animateFloatAsState(
        targetValue = if (isLongPressing) 1f else 0f,
        animationSpec = tween(durationMillis = 2500, easing = LinearEasing),
        label = "manualCaptureProgress"
    )
    
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    
    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
                val delta = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)
                
                isStable = delta < 0.5f 
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        
        onDispose {
            sensorManager.unregisterListener(listener)
            cameraExecutor.shutdown()
        }
    }

    val triggerCapture = {
        if (!isCapturing) {
            isCapturing = true
            val photoFile = File(context.filesDir, "mole_.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture?.takePicture(
                outputOptions, ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        isCapturing = false
                        onError(exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        isCapturing = false
                        onPhotoTaken(photoFile.absolutePath)
                    }
                }
            )
        }
    }

    LaunchedEffect(isStable, isFocused, isMoleDetected, isCapturing, smartCameraEnabled) {
        if (smartCameraEnabled && isStable && isFocused && isMoleDetected && !isCapturing) {
            val duration = 1500
            val steps = 30
            val delayMs = duration / steps
            for (i in 1..steps) {
                delay(delayMs.toLong())
                autoCaptureProgress = i.toFloat() / steps
            }
            triggerCapture()
        } else {
            autoCaptureProgress = 0f
        }
    }

    LaunchedEffect(manualCaptureProgress) {
        if (manualCaptureProgress == 1f) {
            triggerCapture()
            isLongPressing = false
        }
    }

    val frameCounter = remember { AtomicInteger(0) }

    val objectDetector = remember {
        AlgorithmicMoleDetector()
    }

    DisposableEffect(objectDetector) {
        onDispose {
            // No resources to close
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(android.util.Size(480, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                        .build()

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                val count = frameCounter.incrementAndGet()
                                if (count % 2 != 0) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                if (!smartCameraEnabled) {
                                    isFocused = true
                                    isMoleDetected = true
                                    isStable = true
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                try {
                                    val fullBitmap = imageProxy.toBitmap()
                                    try {
                                        val thumbnailWidth = 200
                                        val aspectRatio = fullBitmap.height.toFloat() / fullBitmap.width
                                        val thumbnailHeight = (thumbnailWidth * aspectRatio).toInt()
                                        val thumbnail = Bitmap.createScaledBitmap(fullBitmap, thumbnailWidth, thumbnailHeight, true)

                                        try {
                                            val isSharp = calculateLaplacianVariance(thumbnail) > 10.0
                                            isFocused = isSharp
                                        } finally {
                                            thumbnail.recycle()
                                        }

                                        isMoleDetected = false
                                        
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            objectDetector.detectInCenter(fullBitmap) { isMole ->
                                                isMoleDetected = isMole
                                            }
                                        }
                                    } finally {
                                        fullBitmap.recycle()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MoleAlgo", "Exception during analysis", e)
                                    isFocused = false
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer
                        )
                    } catch (exc: Exception) {
                        onError(exc)
                    }

                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isLongPressing = true
                            tryAwaitRelease()
                            isLongPressing = false
                        }
                    )
                }
        )

        val targetColor = if (isStable && isFocused && isMoleDetected) Color.Green else Color.White
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = targetColor,
                radius = 120f,
                style = Stroke(width = 6f)
            )
            drawCircle(
                color = targetColor.copy(alpha = 0.5f),
                radius = 200f,
                style = Stroke(width = 2f)
            )
            
            if (autoCaptureProgress > 0f) {
                drawArc(
                    color = Color.Green,
                    startAngle = -90f,
                    sweepAngle = 360f * autoCaptureProgress,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - 220f, center.y - 220f),
                    size = androidx.compose.ui.geometry.Size(440f, 440f),
                    style = Stroke(width = 12f)
                )
            }
            
            if (manualCaptureProgress > 0f) {
                drawArc(
                    color = Color.Yellow,
                    startAngle = -90f,
                    sweepAngle = 360f * manualCaptureProgress,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - 220f, center.y - 220f),
                    size = androidx.compose.ui.geometry.Size(440f, 440f),
                    style = Stroke(width = 16f)
                )
            }
        }

        val uiMessage = if (!smartCameraEnabled) {
            "Premi o tieni premuto il pulsante"
        } else when {
            isCapturing -> "Scatto in corso..."
            !isMoleDetected -> "Centrare nel cerchio l'imperfezione"
            !isFocused -> "Metti a fuoco il neo"
            !isStable -> "Tieni fermo il telefono"
            else -> "Acquisizione automatica..."
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = CircleShape,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = uiMessage,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            
            if (smartCameraEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(containerColor = if (isStable) Color.Green else Color.Red) { Text("Stabilità") }
                    Badge(containerColor = if (isFocused) Color.Green else Color.Red) { Text("Fuoco") }
                    Badge(containerColor = if (isMoleDetected) Color.Green else Color.Red) { Text("Neo") }
                }
            }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            IconButton(
                onClick = triggerCapture,
                modifier = Modifier
                    .padding(32.dp)
                .size(64.dp)
                .background(Color.White.copy(alpha = 0.5f), CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                Icons.Default.Camera,
                contentDescription = stringResource(R.string.camera_shoot_desc),
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            }
        }
    }
}

private fun calculateLaplacianVariance(bitmap: Bitmap): Double {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 3 || height < 3) return 0.0

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var sum = 0.0
    var sumSq = 0.0
    val count = (width - 2) * (height - 2)

    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val center = (pixels[y * width + x] and 0xFF).toDouble()
            val left = (pixels[y * width + (x - 1)] and 0xFF).toDouble()
            val right = (pixels[y * width + (x + 1)] and 0xFF).toDouble()
            val top = (pixels[(y - 1) * width + x] and 0xFF).toDouble()
            val bottom = (pixels[(y + 1) * width + x] and 0xFF).toDouble()

            val laplacian = left + right + top + bottom - 4 * center
            sum += laplacian
            sumSq += laplacian * laplacian
        }
    }

    val mean = sum / count
    return (sumSq / count) - (mean * mean)
}


