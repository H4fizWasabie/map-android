package app.map.android

import android.content.Context

/** Task completion and undo logic shared by Home and Calendar, which each render their own task rows. */
object TaskActions {
    fun complete(context: Context, database: TaskDatabase, task: Task): UndoState {
        ReminderScheduler.cancel(context, task.id)
        val completedAt = System.currentTimeMillis()
        val nextDueAt = database.nextDueAt(task, completedAt)
        val nextId = database.complete(task)
        if (nextId != null && nextDueAt != null) ReminderScheduler.schedule(context, nextId, task.title, nextDueAt)
        return UndoState(task, nextId)
    }

    fun undo(context: Context, database: TaskDatabase, state: UndoState) {
        ReminderScheduler.cancel(context, state.nextId ?: state.task.id)
        if (database.undoComplete(state.task, state.nextId)) {
            state.task.dueAt?.takeIf { it > System.currentTimeMillis() }?.let {
                ReminderScheduler.schedule(context, state.task.id, state.task.title, it)
            }
        }
    }
}
