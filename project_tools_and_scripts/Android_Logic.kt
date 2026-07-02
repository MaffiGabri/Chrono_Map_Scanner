package com.example.neimap

import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions

class MoleTrackerLogic {

    // 1. Configurazione del Detector con il modello custom (.tflite)
    // Il modello deve trovarsi nella cartella `assets` del progetto Android
    private val customObjectDetectorOptions =
        CustomObjectDetectorOptions.Builder(
            CustomObjectDetectorOptions.Builder()
                .setLocalModel(
                    com.google.mlkit.vision.objects.custom.LocalModel.Builder()
                        .setAssetFilePath("modello_detector.tflite")
                        .build()
                )
                .build()
        )
        .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE) // Modalità in tempo reale
        .enableClassification()
        .setClassificationConfidenceThreshold(0.8f) // Solo rilevamenti con confidence > 80%
        .setMaxPerObjectLabelCount(1)
        .build()

    private val objectDetector = ObjectDetection.getClient(customObjectDetectorOptions)

    // Variabile per evitare scatti continui (es. debounce)
    private var isPhotoTaken = false

    /**
     * Funzione chiamata ad ogni frame della fotocamera (es. da CameraX ImageAnalysis)
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun processImageProxy(imageProxy: ImageProxy, screenWidth: Int, screenHeight: Int) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    for (detectedObject in detectedObjects) {
                        val boundingBox: Rect = detectedObject.boundingBox

                        // Controlla che sia stato rilevato un "mole" con alta confidence
                        val label = detectedObject.labels.firstOrNull { it.text == "mole" }
                        
                        if (label != null && label.confidence > 0.80f) {
                            Log.d("MoleTracker", "Neo rilevato! Confidence: ${label.confidence}")
                            
                            // 2. Controllo se il neo è ben inquadrato (centrato)
                            // Calcoliamo una "safe zone" centrale del 50% dello schermo
                            val safeZoneLeft = screenWidth * 0.25
                            val safeZoneRight = screenWidth * 0.75
                            val safeZoneTop = screenHeight * 0.25
                            val safeZoneBottom = screenHeight * 0.75

                            val isCentered = boundingBox.centerX() > safeZoneLeft &&
                                             boundingBox.centerX() < safeZoneRight &&
                                             boundingBox.centerY() > safeZoneTop &&
                                             boundingBox.centerY() < safeZoneBottom

                            // 3. Se è centrato ed è la prima volta, scatta la foto
                            if (isCentered && !isPhotoTaken) {
                                scattaFoto()
                                isPhotoTaken = true
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MoleTracker", "Errore nel rilevamento", e)
                }
                .addOnCompleteListener {
                    imageProxy.close() // Chiudi l'immagine per permettere a CameraX di ricevere il frame successivo
                }
        } else {
            imageProxy.close()
        }
    }

    private fun scattaFoto() {
        Log.i("MoleTracker", "CLICK! Il neo è ben inquadrato. Foto scattata automaticamente.")
        // Qui inserisci la logica per salvare l'immagine su disco
    }
    
    fun resetPhotoTrigger() {
        isPhotoTaken = false
    }
}
