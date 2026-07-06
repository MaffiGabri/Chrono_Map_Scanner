package com.example.skinhistoryscanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.skinhistoryscanner.R
import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.domain.UserSettings
import com.example.skinhistoryscanner.ui.getBodyImageRes
import java.io.File
import java.time.format.DateTimeFormatter

object ReportGeneratorWrapper {

    private const val PAGE_WIDTH = 595

    fun drawMolePages(
        document: android.graphics.pdf.PdfDocument,
        context: Context,
        mole: Mole,
        userSettings: UserSettings,
        colorLabel: String,
        history: List<HistoryEntry>,
        startPageNum: Int
    ): Int {
        var pageNum = startPageNum
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_WIDTH, 842, pageNum).create() // 842 is standard A4 height
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Title
        val sideLower = mole.side.lowercase()
        val viewTypeStr = if (sideLower.contains("front")) {
            context.getString(R.string.front)
        } else if (sideLower.contains("back")) {
            context.getString(R.string.back)
        } else {
            mole.side
        }
        val title = "Report ${mole.profileName} - Vista: $viewTypeStr"
        canvas.drawText(title, 20f, 50f, paint)

        // Body Map Thumbnail
        val bodyRes = getBodyImageRes(userSettings, mole.side)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4 // Decode at 1/4 resolution to save memory and speed up generation
        }
        val bodyBitmap = BitmapFactory.decodeResource(context.resources, bodyRes, options)
        if (bodyBitmap != null) {
            val mapRect = RectF(PAGE_WIDTH - 150f - 20f, 20f, PAGE_WIDTH - 20f, 150f + 20f) // Top right corner
            
            // Calculate srcRect for zoom factor around mole
            val bw = bodyBitmap.width.toFloat()
            val bh = bodyBitmap.height.toFloat()
            val cx = bw * (mole.x / 100f)
            val cy = bh * (mole.y / 100f)
            val srcSide = bw * 0.3f
            
            var srcLeft = cx - srcSide / 2f
            var srcTop = cy - srcSide / 2f
            var srcRight = cx + srcSide / 2f
            var srcBottom = cy + srcSide / 2f

            // Clamp
            if (srcLeft < 0f) {
                srcRight -= srcLeft
                srcLeft = 0f
            }
            if (srcTop < 0f) {
                srcBottom -= srcTop
                srcTop = 0f
            }
            if (srcRight > bw) {
                srcLeft -= (srcRight - bw)
                srcRight = bw
            }
            if (srcBottom > bh) {
                srcTop -= (srcBottom - bh)
                srcBottom = bh
            }
            val srcRect = android.graphics.Rect(srcLeft.toInt(), srcTop.toInt(), srcRight.toInt(), srcBottom.toInt())

            // Create a small cropped bitmap to avoid embedding the entire high-res body image in the PDF
            val croppedBitmap = Bitmap.createBitmap(bodyBitmap, srcRect.left, srcRect.top, srcRect.width(), srcRect.height())
            // Scale it down to match the mapRect size (about 130x130)
            val scaledCropped = Bitmap.createScaledBitmap(croppedBitmap, mapRect.width().toInt(), mapRect.height().toInt(), true)

            // Draw rounded thumbnail
            canvas.save()
            val clipPath = android.graphics.Path()
            clipPath.addRoundRect(mapRect, 16f, 16f, android.graphics.Path.Direction.CW)
            canvas.clipPath(clipPath)
            
            canvas.drawBitmap(scaledCropped, null, mapRect, null)
            canvas.restore()
            
            if (croppedBitmap != scaledCropped) croppedBitmap.recycle()
            scaledCropped.recycle()

            // Draw Map Border
            val mapBorderPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(mapRect, 16f, 16f, mapBorderPaint)

            // Draw marker
            val paintMarker = Paint().apply {
                color = parseColorHex(mole.color)
                style = Paint.Style.FILL
            }
            
            val markerX = mapRect.left + ((cx - srcLeft) / srcSide) * mapRect.width()
            val markerY = mapRect.top + ((cy - srcTop) / srcSide) * mapRect.height()
            
            canvas.drawCircle(markerX, markerY, 8f, paintMarker)

            val paintMarkerStroke = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawCircle(markerX, markerY, 8f, paintMarkerStroke)

            bodyBitmap.recycle()
        }

        // Details
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        var currentY = 100f

        canvas.drawText("${context.getString(R.string.report_category)} $colorLabel", 20f, currentY, paint)
        currentY += 30f
        val latestDate = history.firstOrNull()?.date?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "N/A"
        canvas.drawText("${context.getString(R.string.report_date)} $latestDate", 20f, currentY, paint)

        currentY = 220f
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.history), 20f, currentY, paint)
        
        currentY += 10f
        val sepLinePaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 2f
        }
        canvas.drawLine(20f, currentY, PAGE_WIDTH - 20f, currentY, sepLinePaint)
        
        currentY += 30f

        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val PAGE_HEIGHT = 842f
        
        for (entry in history) {
            // Pre-calculate block height
            var blockHeight = 24f // Date
            if (!entry.notes.isNullOrBlank()) blockHeight += 24f
            
            var imgBitmap: Bitmap? = null
            if (entry.imagePath != null) {
                val file = File(entry.imagePath)
                if (file.exists()) {
                    try {
                        imgBitmap = loadCompressedBitmap(file, userSettings.pdfQuality)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            var imgDrawHeight = 0f
            if (imgBitmap != null) {
                val imgWidth = PAGE_WIDTH - 90f // 40f left margin + 30f timeline offset + 20f right margin
                val ratio = imgWidth / imgBitmap.width
                imgDrawHeight = imgBitmap.height * ratio
                blockHeight += imgDrawHeight + 20f
            } else {
                blockHeight += 20f
            }
            blockHeight += 20f // Bottom margin of the entry

            // Check if we need to turn page
            if (currentY + blockHeight > PAGE_HEIGHT - 40f) {
                document.finishPage(page)
                pageNum++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT.toInt(), pageNum).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = 60f

                val contPaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.BLACK
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("Report ${mole.profileName} - (Continua)", 20f, currentY, contPaint)
                currentY += 40f
            }

            // Draw Timeline Graphics
            val timelinePaint = Paint().apply {
                color = parseColorHex(mole.color)
                style = Paint.Style.FILL
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            
            val bubbleRadius = 8f
            val bubbleY = currentY + 12f
            val timelineX = 40f
            
            canvas.drawLine(timelineX, bubbleY, timelineX, currentY + blockHeight, linePaint)
            canvas.drawCircle(timelineX, bubbleY, bubbleRadius, timelinePaint)
            
            val innerPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawCircle(timelineX, bubbleY, bubbleRadius - 3f, innerPaint)

            // Draw block
            val textX = 70f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(entry.date.format(DateTimeFormatter.ISO_LOCAL_DATE), textX, currentY + 16f, paint)
            currentY += 24f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            if (!entry.notes.isNullOrBlank()) {
                canvas.drawText(entry.notes, textX, currentY + 16f, paint)
                currentY += 24f
            }

            if (imgBitmap != null) {
                val imgWidth = PAGE_WIDTH - 90f
                val imgRect = RectF(textX, currentY, textX + imgWidth, currentY + imgDrawHeight)
                
                val imgBorderPaint = Paint().apply {
                    color = Color.LTGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawBitmap(imgBitmap, null, imgRect, null)
                canvas.drawRect(imgRect, imgBorderPaint)
                
                currentY += imgDrawHeight + 20f
                imgBitmap.recycle()
            } else {
                currentY += 20f
            }
            currentY += 20f
        }

        document.finishPage(page)
        return pageNum + 1
    }

    private fun parseColorHex(hex: String): Int {
        val clean = hex.removePrefix("#")
        return try {
            Color.parseColor("#$clean")
        } catch(e: Exception) {
            Color.BLACK
        }
    }

    private fun loadCompressedBitmap(file: File, quality: com.example.skinhistoryscanner.data.domain.PdfQuality): Bitmap? {
        val maxDim = when (quality) {
            com.example.skinhistoryscanner.data.domain.PdfQuality.LOW -> 300
            com.example.skinhistoryscanner.data.domain.PdfQuality.MEDIUM -> 600
            com.example.skinhistoryscanner.data.domain.PdfQuality.HIGH -> 1024
        }
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)
        
        var inSampleSize = 1
        val maxOption = Math.max(options.outWidth, options.outHeight)
        while (maxOption / inSampleSize > maxDim) {
            inSampleSize *= 2
        }
        
        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        options.inPreferredConfig = Bitmap.Config.RGB_565 // Forza l'assenza di canale alpha per dimezzare il peso in RAM e nel PDF
        val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
        if (bmp != null) {
            val scale = Math.min(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
            if (scale < 1f) {
                val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                if (scaled != bmp) {
                    bmp.recycle()
                    return scaled
                }
            }
        }
        return bmp
    }
}
