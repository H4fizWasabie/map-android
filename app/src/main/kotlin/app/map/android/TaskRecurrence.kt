package app.map.android

import java.util.Calendar

/** Pure recurrence math, kept free of the database/Context so it is unit-testable in isolation. */
object TaskRecurrence {
    fun nextDueAt(task: Task, completedAt: Long): Long? {
        val base = Calendar.getInstance().apply { timeInMillis = task.dueAt ?: completedAt }
        when (task.recurrence) {
            "Daily" -> base.add(Calendar.DAY_OF_YEAR, 1)
            "Weekly" -> base.add(Calendar.WEEK_OF_YEAR, 1)
            "Monthly" -> base.add(Calendar.MONTH, 1)
            else -> return null
        }
        return base.timeInMillis
    }
}
