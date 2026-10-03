package app.map.android

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExactReminderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val taskId = 9_000_000_000L + System.currentTimeMillis() % 1_000_000

    @After
    fun tearDown() = ReminderScheduler.cancel(context, taskId)

    @Test
    fun reminderIsScheduledAsAnExactAlarm() {
        val at = System.currentTimeMillis() + 3_600_000L
        ReminderScheduler.schedule(context, taskId, "Exact reminder fixture", at)
        val dump = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys alarm"))
            .bufferedReader().readText()
        val lines = dump.lines()
        val entry = lines.indices.firstOrNull { lines[it].contains("origWhen $at") && lines[it].contains(context.packageName) }
            ?: error("The reminder alarm was not registered:\n" + lines.filter { it.contains(context.packageName) }.take(10))
        val details = lines.drop(entry).take(6).joinToString("\n")
        assertTrue("Reminder was not an exact alarm:\n$details", Regex("window=0\\b").containsMatchIn(details) || "exactAllowReason" in details)
    }
}
