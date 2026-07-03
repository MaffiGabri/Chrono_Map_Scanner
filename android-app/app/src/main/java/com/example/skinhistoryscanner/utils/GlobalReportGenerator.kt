package com.example.skinhistoryscanner.utils

import android.content.Context
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
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(context.getString(R.string.report_title_global), PAGE_WIDTH / 2f, 80f, paint)
        paint.textSize = 20f
        canvas.drawText("Profile: $profileName", PAGE_WIDTH / 2f, 120f, paint)

        // Draw Front Map
        drawGlobalMap(context, canvas, moles, userSettings, "front", RectF(50f, 180f, 250f, 680f))
        // Draw Back Map
        drawGlobalMap(context, canvas, moles, userSettings, "back", RectF(PAGE_WIDTH - 250f - 50f, 180f, PAGE_WIDTH - 50f, 680f))

        document.finishPage(page)

        // 2. Sort Moles
        val sortedMoles = moles.sortedWith(compareBy<Mole> { getSeverityIndex(it.color) }.thenByDescending { mole ->
            mole.history.maxOfOrNull { it.date }
        })

        // 3. Pages for individual moles
        var pageNum = 2
        for (mole in sortedMoles) {
            val colorLabel = getColorLabel(mole.color)
            val molePageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            val molePage = document.startPage(molePageInfo)

            ReportGeneratorWrapper.drawMolePage(context, molePage.canvas, mole, userSettings, colorLabel, mole.history.sortedByDescending { it.date })

            document.finishPage(molePage)
            pageNum++
        }

        val outputFile = File(context.cacheDir, "global_${profileName}_report.pdf")
        document.writeTo(FileOutputStream(outputFile))
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
        val bodyBitmap = BitmapFactory.decodeResource(context.resources, bodyRes)
        if (bodyBitmap != null) {
            canvas.drawBitmap(bodyBitmap, null, rect, null)

            val sideMoles = moles.filter { it.side.contains(side, ignoreCase = true) }
            for (mole in sideMoles) {
                val paintMarker = Paint().apply {
                    color = parseColorHex(mole.color)
                    style = Paint.Style.FILL
                }
                val markerX = rect.left + (mole.x / 100f) * rect.width()
                val markerY = rect.top + (mole.y / 100f) * rect.height()
                canvas.drawCircle(markerX, markerY, 8f, paintMarker)

                val paintMarkerStroke = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawCircle(markerX, markerY, 8f, paintMarkerStroke)
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
