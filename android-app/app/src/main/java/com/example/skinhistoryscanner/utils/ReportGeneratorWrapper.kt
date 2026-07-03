package com.example.skinhistoryscanner.utils

import android.content.Context
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

    fun drawMolePage(
        context: Context,
        canvas: Canvas,
        mole: Mole,
        userSettings: UserSettings,
        colorLabel: String,
        history: List<HistoryEntry>
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Title
        val title = context.getString(R.string.report_title_mole)
        canvas.drawText(title, 20f, 40f, paint)

        // Body Map Thumbnail
        val bodyRes = getBodyImageRes(userSettings, mole.side)
        val bodyBitmap = BitmapFactory.decodeResource(context.resources, bodyRes)
        if (bodyBitmap != null) {
            val mapRect = RectF(PAGE_WIDTH - 150f - 20f, 20f, PAGE_WIDTH - 20f, 150f + 20f) // Top right corner
            canvas.drawBitmap(bodyBitmap, null, mapRect, null)

            // Draw marker
            val paintMarker = Paint().apply {
                color = parseColorHex(mole.color)
                style = Paint.Style.FILL
            }
            val markerX = mapRect.left + (mole.x / 100f) * mapRect.width()
            val markerY = mapRect.top + (mole.y / 100f) * mapRect.height()
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
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        var currentY = 80f

        canvas.drawText("${context.getString(R.string.report_category)} $colorLabel", 20f, currentY, paint)
        currentY += 30f
        val latestDate = history.firstOrNull()?.date?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "N/A"
        canvas.drawText("${context.getString(R.string.report_date)} $latestDate", 20f, currentY, paint)

        currentY = 200f
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.history), 20f, currentY, paint)
        currentY += 30f

        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        for (entry in history) {
            canvas.drawText("Data: ${entry.date.format(DateTimeFormatter.ISO_LOCAL_DATE)}", 20f, currentY, paint)
            currentY += 20f

            if (!entry.notes.isNullOrBlank()) {
                canvas.drawText("Note: ${entry.notes}", 20f, currentY, paint)
                currentY += 20f
            }

            if (entry.imagePath != null) {
                val file = File(entry.imagePath)
                if (file.exists()) {
                    try {
                        val imgBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (imgBitmap != null) {
                            val imgWidth = PAGE_WIDTH - 40f
                            val ratio = imgWidth / imgBitmap.width
                            val imgHeight = imgBitmap.height * ratio

                            val imgRect = RectF(20f, currentY, 20f + imgWidth, currentY + imgHeight)
                            canvas.drawBitmap(imgBitmap, null, imgRect, null)
                            currentY += imgHeight + 20f
                            imgBitmap.recycle()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                currentY += 20f
            }
            currentY += 20f
        }
    }

    private fun parseColorHex(hex: String): Int {
        val clean = hex.removePrefix("#")
        return try {
            Color.parseColor("#$clean")
        } catch(e: Exception) {
            Color.BLACK
        }
    }
}
