package app.map.android

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskRecurrenceTest {

    private fun taskAt(recurrence: String, dueAt: Long?): Task = Task(
        id = 1,
        title = "Test task",
        notes = "",
        dueAt = dueAt,
        recurrence = recurrence,
        tags = "",
        completed = false,
        completedAt = null,
    )

    private fun calendarAt(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `no recurrence returns null`() {
        val dueAt = calendarAt(2026, Calendar.MARCH, 10).timeInMillis
        assertNull(TaskRecurrence.nextDueAt(taskAt("Does not repeat", dueAt), dueAt))
        assertNull(TaskRecurrence.nextDueAt(taskAt("", dueAt), dueAt))
    }

    @Test
    fun `daily recurrence advances one day from the due date`() {
        val due = calendarAt(2026, Calendar.MARCH, 10)
        val expected = calendarAt(2026, Calendar.MARCH, 11)
        val next = TaskRecurrence.nextDueAt(taskAt("Daily", due.timeInMillis), completedAt = due.timeInMillis + 60_000)
        assertEquals(expected.timeInMillis, next)
    }

    @Test
    fun `weekly recurrence advances seven days from the due date`() {
        val due = calendarAt(2026, Calendar.MARCH, 10)
        val expected = calendarAt(2026, Calendar.MARCH, 17)
        val next = TaskRecurrence.nextDueAt(taskAt("Weekly", due.timeInMillis), completedAt = due.timeInMillis)
        assertEquals(expected.timeInMillis, next)
    }

    @Test
    fun `monthly recurrence advances one month from the due date`() {
        val due = calendarAt(2026, Calendar.MARCH, 10)
        val expected = calendarAt(2026, Calendar.APRIL, 10)
        val next = TaskRecurrence.nextDueAt(taskAt("Monthly", due.timeInMillis), completedAt = due.timeInMillis)
        assertEquals(expected.timeInMillis, next)
    }

    @Test
    fun `monthly recurrence clamps to the shorter month instead of rolling over`() {
        // Jan 31 plus one month has no Feb 31; Calendar.add clamps to the last day of February.
        val due = calendarAt(2026, Calendar.JANUARY, 31)
        val expected = calendarAt(2026, Calendar.FEBRUARY, 28)
        val next = TaskRecurrence.nextDueAt(taskAt("Monthly", due.timeInMillis), completedAt = due.timeInMillis)
        assertEquals(expected.timeInMillis, next)
    }

    @Test
    fun `unscheduled recurring task bases the next occurrence on completion time`() {
        val completedAt = calendarAt(2026, Calendar.MARCH, 10, hour = 15, minute = 30).timeInMillis
        val expected = calendarAt(2026, Calendar.MARCH, 11, hour = 15, minute = 30).timeInMillis
        val next = TaskRecurrence.nextDueAt(taskAt("Daily", dueAt = null), completedAt)
        assertEquals(expected, next)
    }
}
