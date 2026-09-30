package app.map.android

import android.content.ContentValues
import android.content.Intent
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
class ScanAcquisitionImportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var device: UiDevice
    private var sessionId = -1L

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
        } finally {
            database.close()
        }
        context.startActivity(Intent(context, ScanActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        assertTrue("MAP Scan did not open", device.wait(Until.hasObject(By.text("Scan documents")), TIMEOUT))
    }

    @After
    fun tearDown() {
        if (!::device.isInitialized) return
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        device.wait(Until.hasObject(By.text("Home")), TIMEOUT)
        if (sessionId <= 0) return

        val database = ScanDatabase(context)
        try {
            database.pages(sessionId).forEach { page ->
                if (page.path.startsWith(File(context.filesDir, "scans/$sessionId").absolutePath)) File(page.path).delete()
                database.writableDatabase.delete("pages", "id = ? AND session_id = ?", arrayOf(page.id.toString(), sessionId.toString()))
            }
            database.writableDatabase.delete("sessions", "id = ?", arrayOf(sessionId.toString()))
        } finally {
            database.close()
        }
        File(context.filesDir, "scans/$sessionId").deleteRecursively()
    }

    @Test
    fun scannerResultReturnsAndImportsIntoTheIsolatedSession() {
        clickText("Scan documents")
        assertTrue("ML Kit scanner UI did not open", waitForExternalScanner())
        // Google ML Kit shows a one-time help overlay on a fresh device.
        clickAction(listOf("got it"), CAPTURE_CONTROL_TIMEOUT)

        clickAction(listOf("capture", "shutter", "take photo", "take picture"), CAPTURE_CONTROL_TIMEOUT)
        // The AVD's virtual-scene feed can be auto-captured before UiAutomator reaches the scanner.
        clickAction(listOf("apply"), CAPTURE_CONTROL_TIMEOUT)
        val completionActions = listOf("done", "finish", "use scan", "use photo", "next", "save")
        val finish = clickAction(completionActions) ?: clickAction(listOf("add page"))
        assertTrue("Scanner did not offer a way to return captured pages. Visible controls: ${visibleControls()}", finish != null)
        // Add page returns to the camera; Preview opens the batch review, where Done returns it.
        if (finish!!.contains("add page")) {
            assertTrue("Scanner did not offer Preview after adding a page. Visible controls: ${visibleControls()}", clickAction(listOf("preview")) != null)
            val finishBatch = clickAction(completionActions)
            assertTrue("Scanner did not offer a completion action after adding a page. Visible controls: ${visibleControls()}", finishBatch != null)
        }

        assertTrue("Scanner did not return to MAP Scan", device.wait(Until.hasObject(By.text("Scan documents")), TIMEOUT))
        val imported = waitForImportedPages()
        assertTrue("Scanner result did not import pages into MAP session $sessionId", imported.isNotEmpty())
        imported.forEachIndexed { index, page ->
            val file = File(page.path)
            assertTrue("Imported page ${index + 1} is empty", file.length() > 0)
            assertTrue("Imported page escaped its session-private directory", page.path.startsWith(File(context.filesDir, "scans/$sessionId").absolutePath))
            assertTrue("Imported page ${index + 1} was not listed in Scan", device.wait(Until.hasObject(By.text("Page ${index + 1}: ${file.name}")), TIMEOUT))
            assertTrue("Imported page ${index + 1} was not selected for export", device.wait(Until.hasObject(By.desc("Include page ${index + 1}")), TIMEOUT))
            assertTrue("Imported page ${index + 1} selection defaulted off", device.findObject(By.desc("Include page ${index + 1}")).isChecked)
        }
        assertTrue("Scan did not show the imported page count", device.wait(Until.hasObject(By.text("Export PDF (${imported.size})")), TIMEOUT))
        assertTrue("Scan export stayed disabled despite the selected imported pages", device.findObject(By.text("Export PDF (${imported.size})")).isEnabled)
    }

    private fun waitForImportedPages(): List<ScanPage> {
        val deadline = android.os.SystemClock.elapsedRealtime() + TIMEOUT
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val database = ScanDatabase(context)
            val pages = try {
                database.pages(sessionId)
            } finally {
                database.close()
            }
            if (pages.isNotEmpty()) return pages
            android.os.SystemClock.sleep(250)
        }
        return emptyList()
    }

    private fun waitForExternalScanner(): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + TIMEOUT
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (device.currentPackageName != context.packageName) return true
            android.os.SystemClock.sleep(200)
        }
        return false
    }

    private fun clickAction(labels: List<String>, timeout: Long = TIMEOUT): String? {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            runCatching { device.findObjects(By.clickable(true)) }.getOrDefault(emptyList()).forEach { control ->
                val label = controlLabel(control) ?: return@forEach
                if (labels.any(label::contains) && runCatching { control.click(); true }.getOrDefault(false)) return label
            }
            android.os.SystemClock.sleep(200)
        }
        return null
    }

    private fun visibleControls(): String = runCatching { device.findObjects(By.clickable(true)) }
        .getOrDefault(emptyList())
        .mapNotNull { control -> runCatching { "${controlLabel(control).orEmpty()} [${control.className} ${control.visibleBounds}]" }.getOrNull() }
        .joinToString(limit = 30)

    private fun controlLabel(control: androidx.test.uiautomator.UiObject2): String? = runCatching {
        "${control.text.orEmpty()} ${control.contentDescription.orEmpty()}".trim().lowercase()
    }.getOrNull()

    private fun clickText(text: String) {
        val selector = By.text(text)
        assertTrue("MAP did not show $text", device.wait(Until.hasObject(selector), TIMEOUT))
        device.findObject(selector)?.click() ?: error("MAP could not click $text")
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val CAPTURE_CONTROL_TIMEOUT = 3_000L
    }
}
