package app.map.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** Turns selected local scan images into a new PDF without changing the source pages. */
internal object ScanPdfExporter {
    fun export(pagePaths: List<String>, output: File): File {
        require(pagePaths.isNotEmpty()) { "No pages selected" }
        require(!output.exists()) { "PDF output already exists" }
        val document = PdfDocument()
        var exportedPages = 0
        try {
            pagePaths.forEach { path ->
                val bitmap = decodePage(path) ?: error(UNREADABLE_PAGE)
                try {
                    val cleaned = ScanImageProcessor.clean(bitmap)
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, exportedPages + 1).create()
                        val pdfPage = document.startPage(pageInfo)
                        drawBitmap(pdfPage.canvas, cleaned)
                        document.finishPage(pdfPage)
                    } finally {
                        if (cleaned !== bitmap) cleaned.recycle()
                    }
                } finally {
                    bitmap.recycle()
                }
                exportedPages++
            }
            check(exportedPages > 0) { "No readable pages" }
            output.parentFile?.mkdirs()
            try {
                FileOutputStream(output).use(document::writeTo)
            } catch (failure: Throwable) {
                output.delete()
                throw failure
            }
            return output
        } finally {
            document.close()
        }
    }

    private fun decodePage(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap) {
        canvas.drawColor(Color.WHITE)
        val margin = 48
        val availableWidth = PAGE_WIDTH - margin * 2
        val availableHeight = PAGE_HEIGHT - margin * 2
        val scale = minOf(availableWidth.toFloat() / bitmap.width, availableHeight.toFloat() / bitmap.height)
        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()
        val left = (PAGE_WIDTH - width) / 2
        val top = (PAGE_HEIGHT - height) / 2
        canvas.drawBitmap(bitmap, null, Rect(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG))
    }

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    const val UNREADABLE_PAGE = "A selected scan page is unavailable"
}
