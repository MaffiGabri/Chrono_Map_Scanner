package com.example.skinhistoryscanner.utils

import android.graphics.Bitmap
import android.graphics.Color

class AlgorithmicMoleDetector {

    /**
     * Analizza ESCLUSIVAMENTE un ritaglio centrale dell'immagine corrispondente al mirino.
     * @param onMoleDetected true se c'è un forte contrasto nel cerchio.
     */
    fun detectInCenter(bitmap: Bitmap, onMoleDetected: (isMoleDetected: Boolean) -> Unit) {
        // Prendiamo un quadrato centrale grande 1/3 del lato minore dell'immagine (corrispondente al mirino UI)
        val cropSize = minOf(bitmap.width, bitmap.height) / 3
        val startX = (bitmap.width - cropSize) / 2
        val startY = (bitmap.height - cropSize) / 2

        val pixels = IntArray(cropSize * cropSize)
        bitmap.getPixels(pixels, 0, cropSize, startX, startY, cropSize, cropSize)

        val histogram = IntArray(256)
        var totalPixels = 0

        // 1. Grayscale e Istogramma solo del crop centrale
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            histogram[lum]++
            totalPixels++
        }
        
        // 2. Otsu's Thresholding con Salvaguardia di Contrasto
        var sum = 0.0
        for (i in 0..255) sum += i * histogram[i]
        
        var sumB = 0.0
        var wB = 0
        var wF = 0
        var varMax = 0.0
        
        var finalMB = 0.0
        var finalMF = 0.0

        for (i in 0..255) {
            wB += histogram[i]
            if (wB == 0) continue
            wF = totalPixels - wB
            if (wF == 0) break

            sumB += (i * histogram[i]).toDouble()
            
            val mB = sumB / wB // Media classe Scritta (Scura, il neo)
            val mF = (sum - sumB) / wF // Media classe Chiara (Pelle)

            val pB = wB.toDouble() / totalPixels
            val pF = wF.toDouble() / totalPixels

            val varBetween = pB * pF * (mB - mF) * (mB - mF)

            if (varBetween > varMax) {
                varMax = varBetween
                finalMB = mB 
                finalMF = mF 
            }
        }

        // SALVAGUARDIA CONTRASTO: la pelle chiara e il neo scuro devono avere una differenza netta.
        val contrast = finalMF - finalMB
        
        // Se il contrasto è maggiore di 20 su 255, c'è una netta macchia scura nel cerchio!
        if (contrast >= 20.0) {
            onMoleDetected(true)
        } else {
            onMoleDetected(false)
        }
    }
}
