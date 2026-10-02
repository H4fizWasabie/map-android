package app.map.android

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Versioned JSON export of tasks. Parsing validates everything before anything is written. */
object TaskBackup {
    const val VERSION = 1
    private const val FORMAT = "map-tasks"

    class InvalidBackup(message: String) : Exception(message)

    fun toJson(tasks: List<Task>, exportedAt: Long): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt)
        .put("tasks", JSONArray().apply {
            tasks.forEach { task ->
                put(JSONObject()
                    .put("title", task.title)
                    .put("notes", task.notes)
                    .put("dueAt", task.dueAt ?: JSONObject.NULL)
                    .put("recurrence", task.recurrence)
                    .put("tags", task.tags)
                    .put("allDay", task.allDay)
                    .put("completed", task.completed)
                    .put("completedAt", task.completedAt ?: JSONObject.NULL))
            }
        })
        .toString(2)

    fun parse(text: String): List<Task> {
        val root = try { JSONObject(text) } catch (_: JSONException) { throw InvalidBackup("This is not a MAP task export.") }
        if (root.optString("format") != FORMAT) throw InvalidBackup("This is not a MAP task export.")
        val version = root.optInt("version", 0)
        if (version < 1) throw InvalidBackup("This export has no valid version.")
        if (version > VERSION) throw InvalidBackup("This export was made by a newer version of MAP. Update MAP to import it.")
        val array = root.optJSONArray("tasks") ?: throw InvalidBackup("This export has no tasks.")
        return try {
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val title = item.getString("title")
                if (title.isBlank()) throw InvalidBackup("Task ${index + 1} has no title.")
                Task(
                    id = 0,
                    title = title,
                    notes = item.optString("notes"),
                    dueAt = item.takeUnless { it.isNull("dueAt") }?.getLong("dueAt"),
                    recurrence = item.optString("recurrence"),
                    tags = item.optString("tags"),
                    completed = item.optBoolean("completed"),
                    completedAt = item.takeUnless { it.isNull("completedAt") }?.getLong("completedAt"),
                    allDay = item.optBoolean("allDay", true),
                )
            }
        } catch (_: JSONException) {
            throw InvalidBackup("This export contains a damaged task.")
        }
    }

    /** Identity used to skip tasks that are already present when importing. */
    fun key(task: Task): String = listOf(task.title, task.dueAt, task.recurrence, task.completed).joinToString("\u0000")
}
