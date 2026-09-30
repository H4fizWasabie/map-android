package app.map.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanPdfExportTest {
    @Test
    fun syntheticPagesBecomeReadableMultiPagePdfWithoutChangingSources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "scan-pdf-test-${System.currentTimeMillis()}").apply { mkdirs() }
        val sources = listOf(File(directory, "page-a.jpg"), File(directory, "page-b.jpg"))
        val output = File(directory, "result.pdf")
        try {
            sources.forEachIndexed { index, file -> createSyntheticPage(file, index) }
            val sourceSizes = sources.map(File::length)

            assertEquals(output, ScanPdfExporter.export(sources.map(File::getAbsolutePath), output))
            assertTrue("PDF output was empty", output.length() > 0)
            assertEquals("Source images changed during export", sourceSizes, sources.map(File::length))
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals("Selected source pages were not each exported", 2, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        assertEquals(595, page.width)
                        assertEquals(842, page.height)
                    }
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingSelectedPageDoesNotProduceAnIncompletePdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "scan-pdf-missing-test-${System.currentTimeMillis()}-").apply { mkdirs() }
        val readable = File(directory, "readable.jpg")
        val missing = File(directory, "missing.jpg")
        val output = File(directory, "result.pdf")
        try {
            createSyntheticPage(readable, 0)
            val failure = runCatching {
                ScanPdfExporter.export(listOf(readable.absolutePath, missing.absolutePath), output)
            }.exceptionOrNull()
            assertNotNull("Export silently skipped an unavailable selected page", failure)
            assertEquals(ScanPdfExporter.UNREADABLE_PAGE, failure!!.message)
            assertFalse("An incomplete PDF was left behind", output.exists())
            assertTrue("The readable source page was changed", readable.isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createSyntheticPage(file: File, index: Int) {
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawRect(45f, 55f, 355f, 545f, Paint().apply { color = Color.rgb(248, 247, 242) })
            canvas.drawText("Synthetic page ${index + 1}", 80f, 130f, Paint().apply {
                color = Color.BLACK
                textSize = 28f
            })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } finally {
            bitmap.recycle()
        }
    }
}
