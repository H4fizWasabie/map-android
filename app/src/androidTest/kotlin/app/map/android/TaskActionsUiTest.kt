package app.map.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.Calendar
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskActionsUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val title = "MAP functional fixture ${System.currentTimeMillis()}"
    private val preferences by lazy { context.getSharedPreferences("map-focus", 0) }
    private lateinit var device: UiDevice
    private var taskId = -1L
    private var hadPinnedId = false
    private var originalPinnedId = -1L

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        hadPinnedId = preferences.contains("pinned_focus_id")
        originalPinnedId = preferences.getLong("pinned_focus_id", -1L)
        preferences.edit().remove("pinned_focus_id").commit()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val database = TaskDatabase(context)
        taskId = try {
            database.addTask(title, "Synthetic UI verification", today, "", "", allDay = true)
        } finally {
            database.close()
        }
        launchMap()
    }

    @After
    fun tearDown() {
        if (taskId > 0) {
            ReminderScheduler.cancel(context, taskId)
            val database = TaskDatabase(context)
            try {
                database.writableDatabase.delete("tasks", "id = ? AND title = ?", arrayOf(taskId.toString(), title))
            } finally {
                database.close()
            }
            FocusWidgetProvider.refresh(context)
        }
        preferences.edit().apply {
            if (hadPinnedId) putLong("pinned_focus_id", originalPinnedId) else remove("pinned_focus_id")
        }.commit()
    }

    @Test
    fun openPinUnpinCompleteAndUndoTask() {
        clickText("Pin focus")
        assertTrue("Pin action did not become Unpin focus", device.wait(Until.hasObject(By.text("Unpin focus")), TIMEOUT))
        clickText("Unpin focus")
        assertTrue("Unpin action did not restore Pin focus", device.wait(Until.hasObject(By.text("Pin focus")), TIMEOUT))

        clickText(title)
        assertTrue("Task details did not open", device.wait(Until.hasObject(By.text("Task details")), TIMEOUT))
        assertTrue("Task title was not editable", device.hasObject(By.desc("Task title")))
        clickText("Mark complete")
        assertTrue("Completed task did not disappear from Home", device.wait(Until.gone(By.text(title)), TIMEOUT))
        clickText("Undo")
        assertTrue("Undo did not restore the task to Home", device.wait(Until.hasObject(By.text(title)), TIMEOUT))

        val database = TaskDatabase(context)
        try {
            assertTrue("Undo did not restore the task as open", database.openTasks().any { it.id == taskId && it.title == title })
            assertFalse("Task remains in completion history after Undo", database.recentCompleted().any { it.id == taskId })
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingOpenTaskRequiresConfirmationAndClearsPinnedFocus() {
        clickText("Pin focus")
        assertTrue("Pin action did not become Unpin focus", device.wait(Until.hasObject(By.text("Unpin focus")), TIMEOUT))
        assertTrue("Task was not pinned", preferences.getLong("pinned_focus_id", -1L) == taskId)

        clickText(title)
        assertTrue("Task details did not open", device.wait(Until.hasObject(By.text("Task details")), TIMEOUT))
        clickText("Delete task")
        assertTrue("Delete confirmation did not open", device.wait(Until.hasObject(By.text("Delete task?")), TIMEOUT))
        clickText("Keep task")
        assertTrue("Canceling delete left the confirmation open", device.wait(Until.gone(By.text("Delete task?")), TIMEOUT))
        assertTrue("Canceling delete removed the task", device.wait(Until.hasObject(By.text(title)), TIMEOUT))

        clickText("Delete task")
        assertTrue("Delete confirmation did not reopen", device.wait(Until.hasObject(By.text("Delete task?")), TIMEOUT))
        clickText("Delete")
        assertTrue("Confirmed delete did not remove the task", device.wait(Until.gone(By.text(title)), TIMEOUT))
        assertFalse("Delete left a stale pinned focus", preferences.contains("pinned_focus_id"))

        val database = TaskDatabase(context)
        try {
            assertTrue("Deleted task remains open", database.openTasks().none { it.id == taskId })
            assertFalse("Deleted task remains in completion history", database.recentCompleted().any { it.id == taskId })
        } finally {
            database.close()
        }
    }

    @Test
    fun taskDetailsActionsRemainReachableAtLargeTextOnNarrowScreen() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        val originalOverride = Regex("Override size: (\\d+x\\d+)")
            .find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            device.executeShellCommand("wm size 960x2133")
            launchMap()
            val tasksTab = device.wait(Until.findObject(By.desc("Tasks navigation")), TIMEOUT)
                ?: error("Tasks destination was not reachable at large text")
            tasksTab.click()
            clickText(title)
            assertTrue("Task details did not open at large text", device.wait(Until.hasObject(By.text("Task details")), TIMEOUT))

            val minimumTarget = (48 * context.resources.displayMetrics.density).toInt()
            listOf("Delete task", "Snooze", "Mark complete", "Save").forEach { label ->
                val target = device.wait(Until.findObject(By.text(label)), TIMEOUT)
                    ?: error("$label was not reachable in narrow, large-text task details")
                val bounds = target.visibleBounds
                assertTrue("$label target was narrower than 48dp: $bounds", bounds.width() >= minimumTarget)
                assertTrue("$label target was shorter than 48dp: $bounds", bounds.height() >= minimumTarget)
                assertTrue("$label target was clipped off-screen: $bounds", bounds.left >= 0 && bounds.right <= device.displayWidth)
            }
        } finally {
            device.executeShellCommand("settings put system font_scale $originalScale")
            device.executeShellCommand(if (originalOverride == null) "wm size reset" else "wm size $originalOverride")
            launchMap()
        }
    }

    private fun clickText(text: String) {
        val selector = By.text(text)
        device.wait(Until.findObject(selector), TIMEOUT)?.click() ?: error("MAP could not click $text")
    }

    private fun launchMap() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        assertTrue("MAP Home did not open", device.wait(Until.hasObject(By.text("Home")), TIMEOUT))
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
