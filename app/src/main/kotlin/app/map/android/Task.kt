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
