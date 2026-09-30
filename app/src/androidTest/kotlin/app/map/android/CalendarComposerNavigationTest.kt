package app.map.android

import android.Manifest
import android.app.PendingIntent
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarComposerNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var device: UiDevice
    private val taskTitle = "MAP route regression ${System.currentTimeMillis()}"
    private val selectedDate = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    private val dayLabel = SimpleDateFormat("EEE\nd", Locale.getDefault()).format(selectedDate.time)
    private val dateLabel = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(selectedDate.time)
    private val weekLabel = "Week of ${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(monday(selectedDate.timeInMillis)))}"

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        launchMap()
        clickText("Calendar")
        assertTrue("Calendar did not open", device.wait(Until.hasObject(By.text("Today")), TIMEOUT))
        clickText(dayLabel)
        assertTrue("Calendar did not select $dateLabel", device.wait(Until.hasObject(By.text(dateLabel)), TIMEOUT))
    }

    @After
    fun tearDown() {
        if (!::device.isInitialized) return
        if (device.hasObject(By.text("New task"))) {
            device.pressBack()
            device.wait(Until.findObject(By.text("Cancel")), 1_000)?.click()
        }
        val database = TaskDatabase(context)
        try {
            database.openTasks().filter { it.title == taskTitle }.forEach { task ->
                ReminderScheduler.cancel(context, task.id)
                database.writableDatabase.delete("tasks", "id = ? AND title = ?", arrayOf(task.id.toString(), taskTitle))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun dateStripExposesFullAccessibleDatesWithFortyEightDpTargets() {
        val dateTargets = device.findObjects(By.clickable(true)).filter { target ->
            target.className.substringAfterLast('.').endsWith("Button") && target.text.orEmpty().contains('\n')
        }
        assertEquals("Calendar should show seven date targets", 7, dateTargets.size)
        assertEquals("Calendar should expose exactly one selected date", 1, dateTargets.count { it.isSelected })
        val minimumSize = (48 * context.resources.displayMetrics.density).toInt()
        dateTargets.forEach { target ->
            val bounds = target.visibleBounds
            assertTrue("Date target was narrower than 48dp: ${target.text} $bounds", bounds.width() >= minimumSize)
            assertTrue("Date target was shorter than 48dp: ${target.text} $bounds", bounds.height() >= minimumSize)
            assertTrue("Date target did not expose a full spoken date", target.contentDescription.contains(','))
        }
    }

    @Test
    fun largeSystemTextKeepsCalendarActionsReadableAndAddTaskReachable() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            context.startActivity(Intent(context, CalendarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val addTaskSelector = By.text("Add task")
            val addTaskBounds = stableBounds(addTaskSelector, "Calendar Add task")
            val requiredWidth = context.resources.displayMetrics.widthPixels - (56 * context.resources.displayMetrics.density).toInt()
            assertTrue("Add task should use a full-width row at large text: $addTaskBounds", addTaskBounds.width() >= requiredWidth)
            assertTrue("Add task should remain at least 48dp high: $addTaskBounds", addTaskBounds.height() >= (48 * context.resources.displayMetrics.density).toInt())

            clickFresh(addTaskSelector, "Calendar Add task")
            assertTrue("Large-text Add task did not open the composer", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
            val titleBounds = stableBounds(By.desc("Task title"), "Task title")
            assertTrue("Task title was not visible in the large-text composer: $titleBounds", titleBounds.height() > 0)
        } finally {
            device.executeShellCommand("settings put system font_scale $originalScale")
            launchMap()
        }
    }

    @Test
    fun composerKeepsCalendarActivityInFrontWhileOpen() {
        clickText("Add task")
        assertTrue("Task composer did not open", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        val activities = device.executeShellCommand("dumpsys activity activities")
        val topActivity = activities.lineSequence()
            .firstOrNull { "topResumedActivity=" in it }
            ?.substringAfter("topResumedActivity=")
            .orEmpty()
        assertTrue("Calendar was replaced while its task composer was open: $topActivity", topActivity.contains("app.map.android/.CalendarActivity"))
    }

    @Test
    fun composerRestoresDraftAndCalendarDateAfterRotation() {
        clickText("Add task")
        val title = device.wait(Until.findObject(By.desc("Task title")), TIMEOUT)
            ?: error("Task title was not reachable in the Calendar composer")
        title.setText(taskTitle)
        device.pressBack()

        try {
            device.setOrientationLeft()
            assertTrue("Task composer did not survive rotation", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
            val restoredTitle = device.wait(Until.findObject(By.desc("Task title")), TIMEOUT)
                ?: error("Task title was not restored after rotation")
            assertEquals("Unsaved task title was lost during rotation", taskTitle, restoredTitle.text)
            assertTrue("Task title was hidden by the landscape keyboard", restoredTitle.visibleBounds.height() > 0)
            device.pressBack() // Close Android's landscape text editor before checking the app behind it.
            assertTrue("Task composer closed with the keyboard", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        } finally {
            device.setOrientationNatural()
            device.wait(Until.findObject(By.text("Cancel")), TIMEOUT)?.click()
        }
        assertSelectedDayRestored("Cancel after rotation")
    }

    @Test
    fun composerSavesTimedRecurringTaskDetailsAndSchedulesReminder() {
        clickText("Add task")
        val title = device.wait(Until.findObject(By.desc("Task title")), TIMEOUT)
            ?: error("Task title was not reachable in the Calendar composer")
        title.setText(taskTitle)
        device.pressBack()
        clickText("Add details")

        val notes = device.wait(Until.findObject(By.desc("Task notes")), TIMEOUT)
            ?: error("Task notes were not revealed")
        notes.setText("Keep the receipt")
        val tags = device.wait(Until.findObject(By.desc("Task tags")), TIMEOUT)
            ?: error("Task tags were not revealed")
        tags.setText("errands, personal")

        clickText(dateLabel)
        assertTrue("Due date picker did not open", device.wait(Until.hasObject(By.text("OK")), TIMEOUT))
        clickText("OK")
        clickText("All day")
        assertTrue("Time picker did not open", device.wait(Until.hasObject(By.text("OK")), TIMEOUT))
        clickText("OK")
        assertTrue("Timed task did not retain its selected time", device.wait(Until.hasObject(By.text("12:00")), TIMEOUT))

        val repeat = device.wait(Until.findObject(By.desc("Task recurrence")), TIMEOUT)
            ?: error("Task recurrence control was not reachable")
        repeat.click()
        clickText("Weekly")
        clickText("Save")

        val database = TaskDatabase(context)
        val savedTask = try {
            database.openTasks().firstOrNull { it.title == taskTitle }
        } finally {
            database.close()
        } ?: error("Composer did not save the scheduled task")
        assertEquals("Task notes did not survive save", "Keep the receipt", savedTask.notes)
        assertEquals("Task tags did not survive save", "errands, personal", savedTask.tags)
        assertEquals("Task recurrence did not survive save", "Weekly", savedTask.recurrence)
        assertFalse("Selected time was saved as an all-day task", savedTask.allDay)
        val dueAt = savedTask.dueAt ?: error("Scheduled task lost its due date")
        assertTrue("Task lost its selected Calendar date", sameDay(dueAt, selectedDate.timeInMillis))
        assertEquals("Task reminder did not retain the selected hour", 12, Calendar.getInstance().apply { timeInMillis = dueAt }.get(Calendar.HOUR_OF_DAY))
        assertEquals("Task reminder did not retain the selected minute", 0, Calendar.getInstance().apply { timeInMillis = dueAt }.get(Calendar.MINUTE))
        assertTrue("Selected reminder time is not in the future", dueAt > System.currentTimeMillis())
        assertTrue("Notification permission was not granted for the reminder check", context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        val reminderUri = "map://task-reminder/${savedTask.id}"
        val reminderIntent = Intent(context, TaskReminderReceiver::class.java).setData(Uri.parse(reminderUri))
        var reminderPendingIntent = PendingIntent.getBroadcast(
            context, 0, reminderIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (reminderPendingIntent == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
            reminderPendingIntent = PendingIntent.getBroadcast(
                context, 0, reminderIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        assertTrue("Saving a scheduled task did not create its reminder PendingIntent", reminderPendingIntent != null)
    }

    @Test
    fun emptyWeekDaysExplainThatNothingIsScheduled() {
        clickText("Week")
        assertTrue("Calendar week did not identify its date range", device.wait(Until.hasObject(By.text(weekLabel)), TIMEOUT))
        assertTrue("Calendar week did not show a clear empty-day state", device.wait(Until.hasObject(By.text("Nothing scheduled")), TIMEOUT))
    }

    @Test
    fun weekViewNamesTheWeekContainingTheSelectedDay() {
        val previousSunday = Calendar.getInstance().apply {
            timeInMillis = monday(System.currentTimeMillis())
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        selectCalendarDate(previousSunday, selectedDate.timeInMillis)
        clickText("Week")

        val expectedWeek = "Week of ${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(monday(previousSunday)))}"
        assertTrue("Calendar did not label the selected week $expectedWeek", device.wait(Until.hasObject(By.text(expectedWeek)), TIMEOUT))
        assertFalse("A previous week was mislabeled as This week", device.hasObject(By.text("This week")))
        assertTrue("Week view did not offer a return to the selected day agenda", device.hasObject(By.text("Agenda")))

        clickText("Agenda")
        val expectedAgendaDate = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(previousSunday))
        assertTrue("Agenda did not preserve the selected date", device.wait(Until.hasObject(By.text(expectedAgendaDate)), TIMEOUT))
        val todayLabel = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        clickFresh(By.text("Today").clickable(true), "Today action")
        assertTrue("Today did not reset Calendar to the current date", device.wait(Until.hasObject(By.text(todayLabel)), TIMEOUT))
    }

    @Test
    fun expandedNavigationLabelsAndCalendarActionsFitAtLargeText() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        val originalOverride = device.executeShellCommand("wm size").lineSequence()
            .firstOrNull { it.startsWith("Override size:") }
            ?.substringAfter(':')?.trim()
        try {
            device.executeShellCommand("wm size 1800x2400")
            device.executeShellCommand("settings put system font_scale 2.0")
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue("Home did not open in the expanded window", device.wait(Until.hasObject(By.desc("Calendar navigation")), TIMEOUT))

            val minimumRailWidth = (160 * context.resources.displayMetrics.density).toInt()
            listOf("Home", "Calendar", "Tasks", "Tools").forEach { label ->
                val target = device.wait(Until.findObject(By.desc("$label navigation")), TIMEOUT)
                    ?: error("$label navigation was not reachable in the expanded window")
                assertTrue("$label rail target was too narrow at large text: ${target.visibleBounds}", target.visibleBounds.width() >= minimumRailWidth)
                assertTrue("$label rail target was shorter than 48dp", target.visibleBounds.height() >= (48 * context.resources.displayMetrics.density).toInt())
            }

            (device.wait(Until.findObject(By.desc("Calendar navigation")), TIMEOUT)
                ?: error("Calendar navigation was not reachable in the expanded window")).click()
            assertTrue("Calendar did not open from the expanded rail", device.wait(Until.hasObject(By.text("Today")), TIMEOUT))
            val addTask = device.wait(Until.findObject(By.text("Add task")), TIMEOUT)
                ?: error("Calendar Add task was not reachable from the expanded rail")
            assertTrue("Calendar actions did not reflow for the narrowed large-text content", addTask.visibleBounds.width() >= (200 * context.resources.displayMetrics.density).toInt())
            addTask.click()
            assertTrue("Expanded large-text Add task did not open the composer", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        } finally {
            device.executeShellCommand("settings put system font_scale $originalScale")
            device.executeShellCommand(if (originalOverride == null) "wm size reset" else "wm size $originalOverride")
            launchMap()
        }
    }

    @Test
    fun homeEmptyFocusStatusRemainsReachableAtLargeTextOnNarrowDisplay() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        val originalOverride = device.executeShellCommand("wm size").lineSequence()
            .firstOrNull { it.startsWith("Override size:") }
            ?.substringAfter(':')?.trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            device.executeShellCommand("wm size 960x2133")
            launchMap()
            device.swipe(480, 1450, 480, 950, 350)
            val status = device.wait(Until.findObject(By.text("Your day is open.")), TIMEOUT)
                ?: error("Truthful empty-state focus status was not reachable at large text")
            val bounds = status.visibleBounds
            assertTrue("Empty-state focus status was not visible at large text: $bounds", bounds.width() > 0 && bounds.height() > 0)
            assertTrue("Empty-state focus status was not enabled", status.isEnabled)
        } finally {
            device.executeShellCommand("settings put system font_scale $originalScale")
            device.executeShellCommand(if (originalOverride == null) "wm size reset" else "wm size $originalOverride")
            launchMap()
        }
    }

    @Test
    fun homeTaskComposerRemainsAvailableWithSystemAnimationsDisabled() {
        val animationScales = listOf("animator_duration_scale", "transition_animation_scale", "window_animation_scale")
            .associateWith { key -> device.executeShellCommand("settings get global $key").trim().toFloatOrNull() ?: 1f }
        try {
            animationScales.keys.forEach { key -> device.executeShellCommand("settings put global $key 0") }
            assertFalse("Android still reports animators enabled", ValueAnimator.areAnimatorsEnabled())

            launchMap()
            val addTaskSelector = By.desc("Add task")
            val addTask = device.wait(Until.findObject(addTaskSelector), TIMEOUT)
                ?: error("Home Add task could not be reached with animations disabled")
            assertTrue("Home primary action should keep a clear visible label", addTask.text.orEmpty().contains("Add task"))
            assertTrue("Home Add task was not visible with animations disabled", addTask.visibleBounds.height() >= (48 * context.resources.displayMetrics.density).toInt())
            addTask.click()
            assertTrue("Home task composer did not open with animations disabled", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
            assertTrue("Task composer did not start with title-first entry", device.wait(Until.hasObject(By.text("Add details")), TIMEOUT))
            assertFalse("Optional task details were visible before being requested", device.hasObject(By.text("Notes (optional)")))
            clickText("Add details")
            assertTrue("Add details did not reveal the optional fields", device.wait(Until.hasObject(By.text("Notes (optional)")), TIMEOUT))
            device.pressBack()
            clickText("Cancel")
        } finally {
            animationScales.forEach { (key, scale) -> device.executeShellCommand("settings put global $key $scale") }
            launchMap()
        }
    }

    @Test
    fun choosingDateAfterTimeResetsComposerToAllDay() {
        clickText("Add task")
        assertTrue("Calendar did not open the task composer", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        clickText("Add details")
        clickText("All day")
        assertTrue("Time picker did not open", device.wait(Until.hasObject(By.text("OK")), TIMEOUT))
        clickText("OK")
        assertTrue("Choosing a time did not update the control", device.wait(Until.hasObject(By.text("12:00")), TIMEOUT))

        clickText(dateLabel)
        assertTrue("Date picker did not open", device.wait(Until.hasObject(By.text("OK")), TIMEOUT))
        clickText("OK")
        assertTrue("Choosing a date left a stale time label", device.wait(Until.hasObject(By.text("All day")), TIMEOUT))

        val title = device.wait(Until.findObject(By.desc("Task title")), TIMEOUT)
            ?: error("Task title was not reachable in the composer")
        title.setText(taskTitle)
        device.pressBack()
        clickText("Save")

        val database = TaskDatabase(context)
        val savedTask = try {
            database.openTasks().firstOrNull { it.title == taskTitle }
        } finally {
            database.close()
        }
        assertTrue("Saved task was not all-day after changing its date", savedTask?.allDay == true)
        assertTrue("Saved task lost the selected Calendar date", savedTask?.dueAt?.let { sameDay(it, selectedDate.timeInMillis) } == true)
    }

    @Test
    fun cancelAndSaveReturnToSelectedCalendarDateAndWeek() {
        clickText("Add task")
        assertTrue("Calendar did not open the task composer", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        device.pressBack() // Hide the title keyboard.
        clickText("Cancel")
        assertSelectedDayRestored("Explicit Cancel")

        clickText("Add task")
        assertTrue("Calendar did not reopen the task composer", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        device.pressBack() // Hide the title keyboard.
        device.click(24, 600) // Tap outside the bottom sheet.
        assertSelectedDayRestored("Outside tap")

        clickText("Add task")
        assertTrue("Calendar did not reopen the task composer", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        device.pressBack() // Hide the title keyboard.
        device.pressBack() // Dismiss the unsaved composer with system Back.
        assertSelectedDayRestored("System Back")

        clickText("Week")
        assertTrue("Calendar did not enter the selected week", device.wait(Until.hasObject(By.text(weekLabel)), TIMEOUT))
        clickText("Add task")
        assertTrue("Calendar did not reopen the composer", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        clickText("Add details")
        assertTrue("Composer did not inherit the selected Calendar date", device.wait(Until.hasObject(By.text(dateLabel)), TIMEOUT))
        val title = device.wait(Until.findObject(By.desc("Task title")), TIMEOUT)
            ?: error("Task title was not reachable in the composer")
        title.setText(taskTitle)
        assertEquals("Task title input did not accept the synthetic test title", taskTitle, title.text)
        device.pressBack()
        clickText("Save")

        assertTrue("Save did not return to the selected week", device.wait(Until.hasObject(By.text(weekLabel)), TIMEOUT))
        val selectedWeekVisible = device.wait(Until.hasObject(By.text(weekLabel)), TIMEOUT)
        val visibleText = device.findObjects(By.clazz("android.widget.TextView"))
            .mapNotNull { it.text?.toString() }
            .joinToString(" | ")
        assertTrue("Save changed the selected week (expected $weekLabel; visible: $visibleText)", selectedWeekVisible)
        assertTrue("Calendar did not show the new selected-date task", device.wait(Until.hasObject(By.text(taskTitle)), TIMEOUT))

        val lookup = TaskDatabase(context)
        val savedTask = try {
            lookup.openTasks().firstOrNull { it.title == taskTitle }
        } finally {
            lookup.close()
        }
        assertTrue("Task was not saved with the selected Calendar date", savedTask?.dueAt?.let { sameDay(it, selectedDate.timeInMillis) } == true)

        val database = TaskDatabase(context)
        try {
            savedTask?.let { task ->
                ReminderScheduler.cancel(context, task.id)
                database.writableDatabase.delete("tasks", "id = ? AND title = ?", arrayOf(task.id.toString(), taskTitle))
            }
        } finally {
            database.close()
        }
        clickText("Agenda")
        clickText("Week")
        assertTrue("Test task cleanup did not refresh Calendar", device.wait(Until.gone(By.text(taskTitle)), TIMEOUT))
    }

    private fun launchMap() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        assertTrue("MAP did not reach Home", device.wait(Until.hasObject(By.text("Home")), TIMEOUT))
    }

    private fun clickText(text: String) {
        val selector = By.text(text)
        device.wait(Until.findObject(selector), TIMEOUT)?.click() ?: error("MAP could not click $text")
    }

    private fun stableBounds(selector: androidx.test.uiautomator.BySelector, label: String): Rect {
        repeat(5) {
            val target = device.wait(Until.findObject(selector), TIMEOUT) ?: error("$label was not reachable")
            try {
                return target.visibleBounds
            } catch (_: StaleObjectException) {
                SystemClock.sleep(100)
            }
        }
        error("$label kept changing while Android applied its new text scale")
    }

    private fun clickFresh(selector: androidx.test.uiautomator.BySelector, label: String) {
        repeat(5) {
            val target = device.wait(Until.findObject(selector), TIMEOUT) ?: error("$label was not reachable")
            try {
                target.click()
                return
            } catch (_: StaleObjectException) {
                SystemClock.sleep(100)
            }
        }
        error("$label kept changing while Android applied its new text scale")
    }

    private fun selectCalendarDate(target: Long, center: Long) {
        var selected = center
        while (target < addDays(selected, -3) || target > addDays(selected, 3)) {
            selected = addDays(selected, if (target < selected) -3 else 3)
            clickFresh(By.desc(calendarDateDescription(selected)), "Calendar date ${calendarDateDescription(selected)}")
        }
        clickFresh(By.desc(calendarDateDescription(target)), "Calendar date ${calendarDateDescription(target)}")
    }

    private fun assertSelectedDayRestored(action: String) {
        assertTrue("$action did not return to Calendar", device.wait(Until.hasObject(By.text(dateLabel)), TIMEOUT))
        assertTrue("$action changed the selected day", device.hasObject(By.text(dateLabel)))
    }

    private fun sameDay(first: Long, second: Long): Boolean = Calendar.getInstance().run {
        timeInMillis = first
        val firstDay = get(Calendar.YEAR) to get(Calendar.DAY_OF_YEAR)
        timeInMillis = second
        firstDay == (get(Calendar.YEAR) to get(Calendar.DAY_OF_YEAR))
    }

    private fun calendarDateDescription(value: Long) = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(value))

    private fun addDays(value: Long, amount: Int): Long = Calendar.getInstance().apply {
        timeInMillis = value
        add(Calendar.DAY_OF_YEAR, amount)
    }.timeInMillis

    private fun monday(value: Long): Long = Calendar.getInstance().apply {
        timeInMillis = value
        add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }.timeInMillis

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
