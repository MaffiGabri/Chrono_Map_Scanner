package com.example.skinhistoryscanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.skinhistoryscanner.R
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.domain.UserSettings
import com.example.skinhistoryscanner.ui.getBodyImageRes
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GlobalReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private val colorOrder = listOf(
        "color_alarm",
        "color_suspicious",
        "color_monitor",
        "color_safe",
        "color_new",
        "color_other"
    )

    private fun getSeverityIndex(color: String): Int {
        val index = colorOrder.indexOf(color)
        return if (index >= 0) index else colorOrder.size
    }

    suspend fun generateGlobalPdf(
        context: Context,
        moles: List<Mole>,
        userSettings: UserSettings,
        profileName: String,
        getColorLabel: (String) -> String
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()

        // 1. Title Page with Body Maps (Front & Back)
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val title = "${context.getString(R.string.report_title_global)} - ${context.getString(R.string.profile)}: $profileName"
        canvas.drawText(title, 20f, 50f, paint)

        val sepLinePaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 2f
        }
        canvas.drawLine(20f, 70f, PAGE_WIDTH - 20f, 70f, sepLinePaint)

        // Draw Front Map
        drawGlobalMap(context, canvas, moles, userSettings, "front", RectF(50f, 120f, 250f, 620f))
        
        // Draw Back Map
        drawGlobalMap(context, canvas, moles, userSettings, "back", RectF(PAGE_WIDTH - 250f, 120f, PAGE_WIDTH - 50f, 620f))

        document.finishPage(page)

        // 2. Sort Moles
        val sortedMoles = moles.sortedWith(compareBy<Mole> { getSeverityIndex(it.color) }.thenByDescending { mole ->
            mole.history.maxOfOrNull { it.date }
        })

        // 3. Pages for individual moles
        var pageNum = 2
        for (mole in sortedMoles) {
            val colorLabel = getColorLabel(mole.color)
            pageNum = ReportGeneratorWrapper.drawMolePages(document, context, mole, userSettings, colorLabel, mole.history.sortedByDescending { it.date }, pageNum)
        }

        val dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val outputFile = File(context.cacheDir, "Report_${profileName}_Globale_${dateStr}.pdf")
        FileOutputStream(outputFile).use { outStream ->
            document.writeTo(outStream)
        }
        document.close()

        outputFile
    }

    suspend fun generateMolePdf(
        context: Context,
        mole: Mole,
        userSettings: UserSettings,
        colorLabel: String
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()

        ReportGeneratorWrapper.drawMolePages(document, context, mole, userSettings, colorLabel, mole.history.sortedByDescending { it.date }, 1)

        val sideLower = mole.side.lowercase()
        val viewName = if (sideLower.contains("front")) context.getString(R.string.front) else if (sideLower.contains("back")) context.getString(R.string.back) else mole.side
        val dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val outputFile = File(context.cacheDir, "Report_${viewName}_${mole.profileName}_${dateStr}.pdf")
        
        FileOutputStream(outputFile).use { outStream ->
            document.writeTo(outStream)
        }
        document.close()

        outputFile
    }

    private fun drawGlobalMap(
        context: Context,
        canvas: Canvas,
        moles: List<Mole>,
        userSettings: UserSettings,
        side: String,
        rect: RectF
    ) {
        val bodyRes = getBodyImageRes(userSettings, side)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4 // Riduce la risoluzione (es. 2000x4000 -> 500x1000) per alleggerire il PDF
        }
        val bodyBitmap = BitmapFactory.decodeResource(context.resources, bodyRes, options)
        if (bodyBitmap != null) {
            val imgRatio = bodyBitmap.width.toFloat() / bodyBitmap.height.toFloat()
            val rectRatio = rect.width() / rect.height()
            
            var finalW = rect.width()
            var finalH = rect.height()
            if (imgRatio > rectRatio) {
                finalH = rect.width() / imgRatio
            } else {
                finalW = rect.height() * imgRatio
            }
            
            val left = rect.left + (rect.width() - finalW) / 2f
            val top = rect.top + (rect.height() - finalH) / 2f
            val finalRect = RectF(left, top, left + finalW, top + finalH)
            
            canvas.drawBitmap(bodyBitmap, null, finalRect, null)

            val mapBorderPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(finalRect, mapBorderPaint)

            val sideMoles = moles.filter { it.side.contains(side, ignoreCase = true) }
            for (mole in sideMoles) {
                val paintMarker = Paint().apply {
                    color = parseColorHex(mole.color)
                    style = Paint.Style.FILL
                }
                val markerX = finalRect.left + (mole.x / 100f) * finalRect.width()
                val markerY = finalRect.top + (mole.y / 100f) * finalRect.height()
                canvas.drawCircle(markerX, markerY, 12f, paintMarker)

                val paintMarkerStroke = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawCircle(markerX, markerY, 12f, paintMarkerStroke)
            }
            bodyBitmap.recycle()
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
