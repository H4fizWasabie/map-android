package app.map.android

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

class TaskDatabase(context: Context) : SQLiteOpenHelper(context, "map.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                due_at INTEGER,
                recurrence TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                completed INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                completed_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX tasks_due_idx ON tasks(completed, due_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun addTask(title: String, notes: String, dueAt: Long?, recurrence: String, tags: String): Long {
        require(title.isNotBlank()) { "Task title cannot be blank" }
        return writableDatabase.insertOrThrow("tasks", null, ContentValues().apply {
            put("title", title)
            put("notes", notes)
            dueAt?.let { put("due_at", it) }
            put("recurrence", recurrence)
            put("tags", tags)
            put("created_at", System.currentTimeMillis())
        })
    }

    fun openTasks(): List<Task> = queryTasks("completed = 0")

    fun recentCompleted(): List<Task> = queryTasks("completed = 1")

    fun complete(task: Task): Long? {
        val completedAt = System.currentTimeMillis()
        val updated = writableDatabase.update(
            "tasks",
            ContentValues().apply {
                put("completed", 1)
                put("completed_at", completedAt)
            },
            "id = ? AND completed = 0",
            arrayOf(task.id.toString())
        )
        if (updated == 0) return null
        val nextDueAt = nextDueAt(task, completedAt) ?: return null
        return addTask(task.title, task.notes, nextDueAt, task.recurrence, task.tags)
    }

    fun snooze(task: Task): Long? {
        val dueAt = System.currentTimeMillis() + DAY
        val updated = writableDatabase.update(
            "tasks",
            ContentValues().apply { put("due_at", dueAt) },
            "id = ? AND completed = 0",
            arrayOf(task.id.toString())
        )
        return if (updated == 1) dueAt else null
    }

    private fun queryTasks(where: String): List<Task> {
        val tasks = mutableListOf<Task>()
        readableDatabase.query(
            "tasks",
            null,
            where,
            null,
            null,
            null,
            if (where == "completed = 1") "completed_at DESC" else "due_at IS NULL, due_at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) tasks += cursor.toTask()
        }
        return tasks
    }

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

    private fun Cursor.toTask() = Task(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        notes = getString(getColumnIndexOrThrow("notes")),
        dueAt = getLongOrNull("due_at"),
        recurrence = getString(getColumnIndexOrThrow("recurrence")),
        tags = getString(getColumnIndexOrThrow("tags")),
        completed = getInt(getColumnIndexOrThrow("completed")) == 1,
        completedAt = getLongOrNull("completed_at")
    )

    private fun Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
