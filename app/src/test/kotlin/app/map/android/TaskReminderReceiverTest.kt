package app.map.android

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReminderReceiverTest {
    private fun task(id: Long, dueAt: Long?) = Task(id, "t$id", "", dueAt, "", "", false, null)

    @Test
    fun restoresOnlyFutureDueTasks() {
        val targets = TaskReminderReceiver.reminderTargets(listOf(task(1, 50), task(2, 150), task(3, null), task(4, 100)), now = 100)
        assertEquals(listOf(2L to 150L), targets.map { it.first.id to it.second })
    }

    @Test
    fun restoresOnBootUpdateAndClockChanges() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        ).forEach { assertTrue(it in TaskReminderReceiver.RESTORE_ACTIONS) }
    }
}
