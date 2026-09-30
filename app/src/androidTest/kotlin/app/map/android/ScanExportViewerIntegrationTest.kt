package app.map.android

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanExportViewerIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var device: UiDevice
    private val token = UUID.randomUUID().toString()
    private val temporaryPagesDirectory by lazy { File(context.filesDir, "scans/map-test-scan-$token") }
    private var sessionId = -1L
    private var nextSessionId = -1L
    private var pageId = -1L
    private var pageFile: File? = null
    private var pdfFile: File? = null
    private var documentUri: String? = null

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        val database = ScanDatabase(context)
        try {
            val newestUnfinished = database.readableDatabase.rawQuery(
                "SELECT COALESCE(MAX(created_at), 0) FROM sessions WHERE exported = 0",
                null
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
            sessionId = database.writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
                put("created_at", maxOf(System.currentTimeMillis(), newestUnfinished + 1))
            })
            temporaryPagesDirectory.mkdirs()
            pageFile = File(temporaryPagesDirectory, "synthetic-page-$token.jpg").also(::writeSyntheticPage)
            pageId = database.addPage(sessionId, requireNotNull(pageFile).absolutePath)
        } finally {
            database.close()
        }
    }

    @After
    fun tearDown() {
        if (!::device.isInitialized) return
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        device.wait(Until.hasObject(By.text("Home")), TIMEOUT)

        val database = ScanDatabase(context)
        try {
            if (pageId > 0 && sessionId > 0) {
                database.writableDatabase.delete("pages", "id = ? AND session_id = ?", arrayOf(pageId.toString(), sessionId.toString()))
            }
            listOf(sessionId, nextSessionId).filter { it > 0 }.forEach { id ->
                database.writableDatabase.delete("sessions", "id = ?", arrayOf(id.toString()))
            }
        } finally {
            database.close()
        }
        val documentUris = buildSet {
            documentUri?.let(::add)
            pdfFile?.takeIf { it.exists() }?.let { file ->
                add(Uri.fromFile(file).toString())
                add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString())
            }
        }
        documentUris.forEach { uri ->
            val documents = DocumentDatabase(context)
            try {
                documents.writableDatabase.delete("document_text", "uri = ?", arrayOf(uri))
                documents.writableDatabase.delete("documents", "uri = ?", arrayOf(uri))
            } finally {
                documents.close()
            }
        }
        pageFile?.delete()
        temporaryPagesDirectory.delete()
        pdfFile?.delete()
    }

    @Test
    fun selectedSyntheticScanExportsRegistersAndOpensInMapViewer() {
        val scansDirectory = File(context.filesDir, "scans")
        val preexistingPdfs = scansDirectory.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .map { it.absolutePath }
            .toSet()

        context.startActivity(Intent(context, ScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("Synthetic page was not shown in Scan", device.wait(Until.hasObject(By.text("Page 1: ${pageFile!!.name}")), TIMEOUT))
        val selectedPage = device.findObject(By.desc("Include page 1"))
        assertNotNull("Scan did not expose the page selection control", selectedPage)
        assertTrue("Synthetic page was not selected by default", selectedPage!!.isChecked)
        val export = device.findObject(By.text("Export PDF (1)"))
        assertNotNull("Scan did not expose an enabled PDF export action", export)
        assertTrue("PDF export action was disabled for the selected page", export!!.isEnabled)
        export.click()

        pdfFile = waitForNewPdf(scansDirectory, preexistingPdfs)
        assertNotNull("Scan did not create a new PDF", pdfFile)
        waitForShareSheet()
        device.pressBack()
        assertTrue("Scan screen did not return after dismissing share", device.wait(Until.hasObject(By.desc("Include page 1")), TIMEOUT))

        val output = requireNotNull(pdfFile)
        val outputUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
        documentUri = outputUri.toString()
        val documents = DocumentDatabase(context)
        try {
            val saved = documents.recent().firstOrNull { it.uri == documentUri }
            assertNotNull("Successful scan export was not added to local Documents", saved)
            assertEquals(output.name, saved!!.name)
            assertTrue("New scan PDF document row is unavailable", saved.available)
        } finally {
            documents.close()
        }
        val scans = ScanDatabase(context)
        try {
            assertEquals("Exported pages still appear as unfinished", 0, scans.unfinishedPageCount())
            nextSessionId = scans.activeSession()
            assertTrue("A completed scan session was reused for the next batch", nextSessionId != sessionId)
            assertTrue("The next scan session should start empty", scans.pages(nextSessionId).isEmpty())
            assertTrue("Export changed or removed the source page", requireNotNull(pageFile).isFile)
        } finally {
            scans.close()
        }

        device.findObject(By.text("Scan documents")).click()
        assertTrue("A later scan did not launch the document scanner", waitForExternalScanner())
        device.wait(Until.findObject(By.text("Got it")), 1_000)?.click()
        SystemClock.sleep(750)
        device.pressBack()
        assertTrue("Canceling the document scanner did not return to Scan", device.wait(Until.hasObject(By.text("Scan documents")), TIMEOUT))
        assertTrue("A later scan reused the completed batch instead of starting empty", device.wait(Until.hasObject(By.text("No pages captured yet.")), TIMEOUT))

        context.startActivity(Intent(context, DocumentViewerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(DocumentViewerActivity.EXTRA_URI, documentUri)
            putExtra(DocumentViewerActivity.EXTRA_NAME, output.name)
            putExtra(DocumentViewerActivity.EXTRA_MIME, "application/pdf")
        })
        assertTrue("MAP did not open the exported PDF", device.wait(Until.hasObject(By.text(output.name)), TIMEOUT))
        assertTrue("MAP did not expose the rendered PDF page", device.wait(Until.hasObject(By.desc("PDF page 1")), TIMEOUT))
    }

    @Test
    fun unavailableScanPageStaysVisibleButCannotBeIncluded() {
        requireNotNull(pageFile).delete()
        context.startActivity(Intent(context, ScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("Unavailable scan page was hidden", device.wait(Until.hasObject(By.text("Page 1: ${pageFile!!.name} (Unavailable)")), TIMEOUT))
        val includePage = device.wait(Until.findObject(By.desc("Include page 1")), TIMEOUT)
        assertNotNull("Unavailable page lost its control", includePage)
        assertFalse("Unavailable page could still be selected", includePage!!.isEnabled)
        assertFalse("Unavailable page remained selected for export", includePage.isChecked)
        val export = device.wait(Until.findObject(By.text("Export PDF (0)")), TIMEOUT)
        assertNotNull("Empty selection did not show the export action", export)
        assertFalse("Export was enabled with no readable pages", export!!.isEnabled)
    }

    private fun writeSyntheticPage(file: File) {
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawRect(45f, 55f, 355f, 545f, Paint().apply { color = Color.rgb(248, 247, 242) })
            canvas.drawText("MAP synthetic test page", 70f, 140f, Paint().apply {
                color = Color.BLACK
                textSize = 24f
            })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun waitForNewPdf(directory: File, oldPaths: Set<String>): File? {
        val deadline = android.os.SystemClock.elapsedRealtime() + TIMEOUT
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            directory.listFiles().orEmpty().firstOrNull {
                it.isFile && it.extension.equals("pdf", ignoreCase = true) && it.absolutePath !in oldPaths
            }?.let { return it }
            android.os.SystemClock.sleep(200)
        }
        return null
    }

    private fun waitForShareSheet() {
        val deadline = android.os.SystemClock.elapsedRealtime() + TIMEOUT
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (device.currentPackageName != context.packageName) return
            android.os.SystemClock.sleep(200)
        }
        throw AssertionError("Scan created its PDF but did not open Android's share sheet")
    }

    private fun waitForExternalScanner(): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + TIMEOUT
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (device.currentPackageName != context.packageName) return true
            android.os.SystemClock.sleep(200)
        }
        return false
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
