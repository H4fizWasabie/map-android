package app.map.android

data class Task(
    val id: Long,
    val title: String,
    val notes: String,
    val dueAt: Long?,
    val recurrence: String,
    val tags: String,
    val completed: Boolean,
    val completedAt: Long?,
    val allDay: Boolean = true
)

/** The task just completed and the id of the recurrence it produced, if any, so completion can be undone. */
data class UndoState(val task: Task, val nextId: Long?)
