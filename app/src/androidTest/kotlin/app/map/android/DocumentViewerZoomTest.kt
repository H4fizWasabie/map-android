package app.map.android

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
    fun pinchGestureZoomsPdfBeyond100Percent() {
        context.startActivity(Intent(context, DocumentViewerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(DocumentViewerActivity.EXTRA_URI, Uri.fromFile(pdfFile).toString())
            putExtra(DocumentViewerActivity.EXTRA_NAME, pdfFile.name)
            putExtra(DocumentViewerActivity.EXTRA_MIME, "application/pdf")
        })
        assertTrue("MAP did not open the PDF viewer", device.wait(Until.hasObject(By.text(pdfFile.name)), TIMEOUT))
        SystemClock.sleep(1_000)

        val centerX = device.displayWidth / 2f
        val centerY = device.displayHeight * 0.6f

        performPinchOut(centerX, centerY, startRadius = 60f, endRadius = 500f)

        assertTrue(
            "Pinch gesture did not raise PDF zoom above 100%",
            device.wait(Until.hasObject(By.textStartsWith("Zoom ")), TIMEOUT)
        )
        val zoomLabel = device.findObject(By.textStartsWith("Zoom "))?.text.orEmpty()
        val percent = Regex("Zoom (\\d+)%").find(zoomLabel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        assertTrue("Expected zoom above 100%, was \"$zoomLabel\"", percent > 100)
    }

    private fun performPinchOut(centerX: Float, centerY: Float, startRadius: Float, endRadius: Float, steps: Int = 12) {
        val automation = instrumentation.uiAutomation
        val downTime = SystemClock.uptimeMillis()
        var time = downTime

        fun props(id: Int) = MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }

        fun coords(x: Float, y: Float) = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }

        fun send(action: Int, time: Long, pointerCount: Int, radius: Float) {
            val pointerProps = arrayOf(props(0), props(1)).copyOfRange(0, pointerCount)
            val pointerCoords = arrayOf(
                coords(centerX - radius, centerY),
                coords(centerX + radius, centerY),
            ).copyOfRange(0, pointerCount)
            val event = MotionEvent.obtain(
                downTime, time, action, pointerCount, pointerProps, pointerCoords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            automation.injectInputEvent(event, true)
            event.recycle()
        }

        send(MotionEvent.ACTION_DOWN, time, 1, startRadius)
        time += 16
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), time, 2, startRadius)

        for (step in 1..steps) {
            time += 16
            val radius = startRadius + (endRadius - startRadius) * step / steps
            send(MotionEvent.ACTION_MOVE, time, 2, radius)
        }

        time += 16
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), time, 2, endRadius)
        time += 16
        send(MotionEvent.ACTION_UP, time, 1, endRadius)
    }

    private fun minimalPdf(): ByteArray = ("%PDF-1.4\n" +
        "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
        "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
        "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 300]/Resources<<>>>>endobj\n" +
        "xref\n0 4\n0000000000 65535 f \n" +
        "trailer<</Size 4/Root 1 0 R>>\nstartxref\n0\n%%EOF").toByteArray()

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
