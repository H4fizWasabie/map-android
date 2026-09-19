package app.map.android

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentViewerZoomTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var device: UiDevice
    private lateinit var pdfFile: File

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        pdfFile = File(context.filesDir, "map-zoom-test.pdf").apply { writeBytes(minimalPdf()) }
    }

    @After
    fun tearDown() {
        pdfFile.delete()
    }

    @Test
    fun zoomButtonRendersPdfAt450Percent() {
        context.startActivity(Intent(context, DocumentViewerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(DocumentViewerActivity.EXTRA_URI, Uri.fromFile(pdfFile).toString())
            putExtra(DocumentViewerActivity.EXTRA_NAME, pdfFile.name)
            putExtra(DocumentViewerActivity.EXTRA_MIME, "application/pdf")
        })
        assertTrue("MAP did not open the PDF viewer", device.wait(Until.hasObject(By.text(pdfFile.name)), TIMEOUT))
        SystemClock.sleep(1_000)

        repeat(14) { device.findObject(By.desc("Zoom in")).click() }

        assertTrue(
            "Zoom button did not raise PDF zoom to 450%",
            device.wait(Until.hasObject(By.text("Zoom 450%")), TIMEOUT)
        )

        val screenshotFile = File(context.cacheDir, "pdf-zoom.png")
        assertTrue("Could not capture the PDF viewer", device.takeScreenshot(screenshotFile))
        val screenshot = BitmapFactory.decodeFile(screenshotFile.path)
        var redPixels = 0
        for (x in 0 until screenshot.width step 8) {
            for (y in 0 until screenshot.height step 8) {
                val pixel = screenshot.getPixel(x, y)
                if (((pixel shr 16) and 0xff) > 180 && ((pixel shr 8) and 0xff) < 100 && (pixel and 0xff) < 100) {
                    redPixels++
                }
            }
        }
        assertTrue("Zoom button changed the label but no rendered PDF page is visible", redPixels > 100)
    }

    private fun minimalPdf(): ByteArray = ("%PDF-1.4\n" +
        "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
        "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
        "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 300]/Resources<</ProcSet[/PDF]>>/Contents 4 0 R>>endobj\n" +
        "4 0 obj<</Length 31>>stream\n1 0 0 rg 0 0 300 300 re f\nendstream\nendobj\n" +
        "xref\n0 5\n0000000000 65535 f \n" +
        "trailer<</Size 5/Root 1 0 R>>\nstartxref\n0\n%%EOF").toByteArray()

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
