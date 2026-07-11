package com.example.chronomapscanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.chronomapscanner.R
import com.example.chronomapscanner.data.domain.Mole
import com.example.chronomapscanner.data.domain.UserSettings
import com.example.chronomapscanner.ui.getBodyImageRes
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
        variants: List<com.example.chronomapscanner.data.local.room.BackgroundVariantEntity>,
        getColorLabel: (String) -> String
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val sepLinePaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 2f
        }

        var currentGlobalPage = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentGlobalPage).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        var variantsProcessed = 0
        if (variants.isEmpty()) {
            val title = "${context.getString(R.string.report_title_global).uppercase()} - ${context.getString(R.string.profile).uppercase()}: ${profileName.uppercase()}"
            canvas.drawText(title, 20f, 50f, paint)
            canvas.drawLine(20f, 70f, PAGE_WIDTH - 20f, 70f, sepLinePaint)
            document.finishPage(page)
        } else {
            for (variant in variants) {
                if (variantsProcessed > 0 && variantsProcessed % 2 == 0) {
                    document.finishPage(page)
                    currentGlobalPage++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentGlobalPage).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                }
                
                if (variantsProcessed % 2 == 0) {
                    val title = "${context.getString(R.string.report_title_global).uppercase()} - ${context.getString(R.string.profile).uppercase()}: ${profileName.uppercase()}"
                    canvas.drawText(title, 20f, 50f, paint)
                    canvas.drawLine(20f, 70f, PAGE_WIDTH - 20f, 70f, sepLinePaint)
                }
                
                val isLeft = variantsProcessed % 2 == 0
                val rect = if (isLeft) RectF(20f, 120f, (PAGE_WIDTH / 2f) - 10f, 800f) else RectF((PAGE_WIDTH / 2f) + 10f, 120f, PAGE_WIDTH - 20f, 800f)
                
                // Draw view title above the map
                val viewTitlePaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.DKGRAY
                    textSize = 20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(variant.name, rect.centerX(), rect.top - 10f, viewTitlePaint)
                
                drawGlobalMap(context, canvas, moles, userSettings, variant, rect)
                
                variantsProcessed++
            }
            if (variantsProcessed > 0) {
                document.finishPage(page)
            }
        }

        // 2. Sort Moles
        val sortedMoles = moles.sortedWith(compareBy<Mole> { getSeverityIndex(it.color) }.thenByDescending { mole ->
            mole.history.maxOfOrNull { it.date }
        })

        // 3. Pages for individual moles
        var pageNum = currentGlobalPage + 1
        for (mole in sortedMoles) {
            val colorLabel = getColorLabel(mole.color)
            val variantName = variants.find { it.id == mole.side }?.name ?: mole.side
            pageNum = ReportGeneratorWrapper.drawMolePages(document, context, mole, userSettings, colorLabel, variantName, mole.history.sortedByDescending { it.date }, pageNum)
        }

        val dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val safeProfileName = profileName.replace(" ", "_").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        val outputFile = File(context.cacheDir, "Report_Globale_${safeProfileName}_${dateStr}.pdf")
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
        colorLabel: String,
        variantName: String? = null
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()

        val sideLower = mole.side.lowercase()
        val viewName = variantName ?: if (sideLower.contains("front")) context.getString(R.string.front) else if (sideLower.contains("back")) context.getString(R.string.back) else mole.side

        ReportGeneratorWrapper.drawMolePages(document, context, mole, userSettings, colorLabel, viewName, mole.history.sortedByDescending { it.date }, 1)

        val dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val safeProfileName = mole.profileName.replace(" ", "_").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        val outputFile = File(context.cacheDir, "Report_${viewName}_${safeProfileName}_${dateStr}.pdf")
        
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
        variant: com.example.chronomapscanner.data.local.room.BackgroundVariantEntity,
        rect: RectF
    ) {
        val bodyBitmap = if (variant.imagePath != null && File(variant.imagePath).exists()) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(variant.imagePath, options)
        } else {
            val bodyRes = getBodyImageRes(userSettings, variant.id, variant.name)
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeResource(context.resources, bodyRes, options)
        }
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

            val sideMoles = moles.filter { it.side == variant.id }
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
