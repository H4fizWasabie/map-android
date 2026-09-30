package app.map.android

import java.util.Calendar

/** Pure recurrence math, kept free of the database/Context so it is unit-testable in isolation. */
object TaskRecurrence {
    fun nextDueAt(task: Task, completedAt: Long): Long? {
        val base = Calendar.getInstance().apply { timeInMillis = task.dueAt ?: completedAt }
        val field = when (task.recurrence) {
            "Daily" -> Calendar.DAY_OF_YEAR
            "Weekly" -> Calendar.WEEK_OF_YEAR
            "Monthly" -> Calendar.MONTH
            else -> return null
        }
        do base.add(field, 1) while (base.timeInMillis <= completedAt)
        return base.timeInMillis
    }
}
