package app.map.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.Calendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotificationActionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val title = "MAP reminder fixture ${System.currentTimeMillis()}"
    private lateinit var device: UiDevice
    private var taskId = -1L
    private val dueAt = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val database = TaskDatabase(context)
        taskId = try { database.addTask(title, "", dueAt, "", "", allDay = false) } finally { database.close() }
    }

    @After
    fun tearDown() {
        device.pressHome()
        ReminderScheduler.cancel(context, taskId)
        context.getSystemService(android.app.NotificationManager::class.java).cancel(taskId.hashCode())
        val database = TaskDatabase(context)
        try { database.writableDatabase.delete("tasks", "id = ?", arrayOf(taskId.toString())) } finally { database.close() }
        FocusWidgetProvider.refresh(context)
    }

    @Test
    fun doneActionCompletesTheTask() {
        tapAction("Done")
        assertTrue("Done did not complete the task", waitForTask { it == null })
    }

    @Test
    fun snoozeActionMovesTheTaskToTomorrow() {
        tapAction("Snooze")
        assertTrue("Snooze did not move the due date", waitForTask { it != null && it.dueAt!! > dueAt + 12 * 3_600_000L })
        assertEquals(false, openTask()?.completed)
    }

    private fun tapAction(label: String) {
        TaskReminderReceiver().onReceive(
            context,
            Intent(context, TaskReminderReceiver::class.java)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                .putExtra(ReminderScheduler.EXTRA_TITLE, title)
        )
        device.openNotification()
        val action = device.wait(Until.findObject(By.text(label)), TIMEOUT) ?: error("Reminder notification did not show $label")
        action.click()
    }

    private fun openTask(): Task? {
        val database = TaskDatabase(context)
        return try { database.openTasks().firstOrNull { it.id == taskId } } finally { database.close() }
    }

    private fun waitForTask(condition: (Task?) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            if (condition(openTask())) return true
            Thread.sleep(250)
        }
        return false
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
