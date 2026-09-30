package app.map.android

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
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
    private var cleanupUri: String? = null

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        pdfFile = File(context.filesDir, "map-zoom-test.pdf").apply { writeBytes(minimalPdf()) }
    }

    @After
    fun tearDown() {
        cleanupUri?.let { uri ->
            val database = DocumentDatabase(context)
            try {
                database.writableDatabase.delete("document_text", "uri = ?", arrayOf(uri))
                database.writableDatabase.delete("documents", "uri = ?", arrayOf(uri))
            } finally {
                database.close()
            }
        }
        pdfFile.delete()
    }

    @Test
    fun zoomButtonRendersPdfAt450Percent() {
        openPdfViewer()
        SystemClock.sleep(1_000)

        repeat(14) { device.findObject(By.desc("Zoom in")).click() }

        assertTrue(
            "Zoom button did not raise PDF zoom to 450%",
            device.wait(Until.hasObject(By.text("Zoom 450%")), TIMEOUT)
        )

        var redPixels = 0
        val screenshotFile = File(context.cacheDir, "pdf-zoom.png")
        val renderDeadline = SystemClock.elapsedRealtime() + TIMEOUT
        while (redPixels <= 100 && SystemClock.elapsedRealtime() < renderDeadline) {
            assertTrue("Could not capture the PDF viewer", device.takeScreenshot(screenshotFile))
            val screenshot = requireNotNull(BitmapFactory.decodeFile(screenshotFile.path))
            try {
                for (x in 0 until screenshot.width step 8) {
                    for (y in 0 until screenshot.height step 8) {
                        val pixel = screenshot.getPixel(x, y)
                        if (((pixel shr 16) and 0xff) > 180 && ((pixel shr 8) and 0xff) < 100 && (pixel and 0xff) < 100) {
                            redPixels++
                        }
                    }
                }
            } finally {
                screenshot.recycle()
            }
            if (redPixels <= 100) SystemClock.sleep(100)
        }
        assertTrue("Zoom button changed the label but no rendered PDF page is visible", redPixels > 100)
    }

    @Test
    fun pinchGestureChangesPdfZoom() {
        openPdfViewer()
        val page = device.wait(Until.findObject(By.desc("PDF page 1")), TIMEOUT)
            ?: error("PDF page was not reachable for a pinch gesture")
        val bounds = page.visibleBounds
        assertTrue("PDF page was too narrow for a real two-finger gesture: $bounds", bounds.width() > 600)

        page.pinchOpen(0.5f)

        val zoom = device.wait(Until.findObject(By.textContains("Zoom ")), TIMEOUT)
        assertTrue(
            "Pinch gesture did not change the PDF zoom: ${zoom?.text}",
            zoom != null && zoom.text != "Zoom 100%"
        )
    }

    @Test
    fun narrowLargeTextKeepsDocumentViewerActionsReachable() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim().ifEmpty { "1.0" }
        val originalSize = device.executeShellCommand("wm size").lineSequence()
            .firstOrNull { it.startsWith("Override size:") }
            ?.substringAfter(':')?.trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            device.executeShellCommand("wm size 960x2133")
            openPdfViewer()

            listOf(
                By.desc("Back"),
                By.desc("Zoom out"),
                By.desc("Reset zoom"),
                By.desc("Zoom in"),
                By.text("Read text"),
                By.text("Search"),
                By.text("Share"),
            ).forEach { selector ->
                val target = device.wait(Until.findObject(selector), TIMEOUT)
                    ?: error("Document viewer action $selector was not reachable")
                val bounds = target.visibleBounds
                val minimum = (48 * context.resources.displayMetrics.density).toInt()
                assertTrue("Document viewer action $selector was narrower than 48dp: $bounds", bounds.width() >= minimum)
                assertTrue("Document viewer action $selector was shorter than 48dp: $bounds", bounds.height() >= minimum)
                assertTrue("Document viewer action $selector was clipped horizontally: $bounds", bounds.left >= 0 && bounds.right <= device.displayWidth)
            }
        } finally {
            if (device.hasObject(By.text(pdfFile.name))) device.pressBack()
            device.executeShellCommand("settings put system font_scale $originalScale")
            device.executeShellCommand(if (originalSize == null) "wm size reset" else "wm size $originalSize")
        }
    }

    @Test
    fun documentSearchJumpsToMatchWithAnimationsDisabled() {
        pdfFile.writeBytes(threePagePdf())
        val uri = Uri.fromFile(pdfFile).toString()
        val database = DocumentDatabase(context)
        try {
            database.saveText(uri, 2, "needle on the third page")
            cleanupUri = uri
        } finally {
            database.close()
        }

        val animationSettings = listOf("animator_duration_scale", "transition_animation_scale", "window_animation_scale")
        val originalScales = animationSettings.associateWith { key ->
            device.executeShellCommand("settings get global $key").trim().ifEmpty { "1.0" }
        }
        try {
            openPdfViewer()
            animationSettings.forEach { key -> device.executeShellCommand("settings put global $key 0") }
            val reducedMotionDeadline = SystemClock.elapsedRealtime() + 2_000
            while (ValueAnimator.areAnimatorsEnabled() && SystemClock.elapsedRealtime() < reducedMotionDeadline) {
                SystemClock.sleep(25)
            }
            assertTrue("Android still reports animators enabled", !ValueAnimator.areAnimatorsEnabled())
            assertTrue("PDF page 1 did not load", device.wait(Until.hasObject(By.desc("PDF page 1")), TIMEOUT))

            device.wait(Until.findObject(By.text("Search")), TIMEOUT)?.click() ?: error("Search was not reachable")
            val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), TIMEOUT)
                ?: error("Search input was not reachable")
            input.setText("needle")
            device.wait(Until.findObject(By.text("Find")), TIMEOUT)?.click() ?: error("Find was not reachable")
            assertTrue("Search did not find the third-page text", device.wait(Until.hasObject(By.text("Found on page 3.")), TIMEOUT))
            assertTrue("Reduced-motion search did not scroll past page 1", device.wait(Until.gone(By.desc("PDF page 1")), TIMEOUT))
            assertTrue("Reduced-motion search did not reveal the matching page", device.wait(Until.hasObject(By.desc("PDF page 3")), TIMEOUT))
        } finally {
            if (device.hasObject(By.text(pdfFile.name))) device.pressBack()
            originalScales.forEach { (key, value) -> device.executeShellCommand("settings put global $key $value") }
        }
    }

    @Test
    fun missingPdfKeepsItsRecordAndOffersRecovery() {
        val uri = Uri.fromFile(pdfFile).toString()
        cleanupUri = uri
        val database = DocumentDatabase(context)
        try {
            database.upsert(uri, pdfFile.name, "application/pdf")
            database.markUnavailable(uri)
        } finally {
            database.close()
        }
        pdfFile.delete()

        context.startActivity(Intent(context, ToolsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        val unavailableDocument = device.wait(Until.findObject(By.desc("Unavailable document: ${pdfFile.name}")), TIMEOUT)
            ?: error("Tools did not expose the unavailable document's accessibility state")
        unavailableDocument.click()
        assertTrue("Missing PDF did not show an unavailable state", device.wait(Until.hasObject(By.text("Document unavailable")), TIMEOUT))
        val recovery = device.wait(Until.findObject(By.text("Open with another app")), TIMEOUT)
            ?: error("Missing PDF did not offer another-app recovery")
        assertTrue("Recovery action was shorter than 48dp", recovery.visibleBounds.height() >= (48 * context.resources.displayMetrics.density).toInt())

        val lookup = DocumentDatabase(context)
        val saved = try {
            lookup.recent().firstOrNull { it.uri == uri }
        } finally {
            lookup.close()
        }
        assertTrue("Unavailable source lost its retained document record", saved != null)
        assertTrue("Missing source was still marked available", saved?.available == false)
        assertTrue("Missing source metadata was changed", saved?.name == pdfFile.name && saved.mime == "application/pdf")
    }

    private fun openPdfViewer() {
        launchDocumentViewer()
        assertTrue("MAP did not open the PDF viewer", device.wait(Until.hasObject(By.text(pdfFile.name)), TIMEOUT))
    }

    private fun launchDocumentViewer() {
        context.startActivity(Intent(context, DocumentViewerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(DocumentViewerActivity.EXTRA_URI, Uri.fromFile(pdfFile).toString())
            putExtra(DocumentViewerActivity.EXTRA_NAME, pdfFile.name)
            putExtra(DocumentViewerActivity.EXTRA_MIME, "application/pdf")
        })
    }

    private fun threePagePdf(): ByteArray {
        val pdf = PdfDocument()
        return try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 300, index + 1).create())
                pdf.finishPage(page)
            }
            ByteArrayOutputStream().use { output ->
                pdf.writeTo(output)
                output.toByteArray()
            }
        } finally {
            pdf.close()
        }
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
