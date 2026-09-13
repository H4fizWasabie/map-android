package app.map.android

import android.content.SharedPreferences
import java.util.Calendar

/** The task Home is currently focused on, the one queued after it, and whether the focus is user-pinned. */
data class FocusSelection(val focus: Task?, val next: Task?, val pinned: Boolean)

/** Shared by Home and the Focus widget so both agree on what "now" and "next" mean. */
object FocusPicker {
    private const val PINNED_FOCUS_ID = "pinned_focus_id"
    private const val HOUR = 60 * 60 * 1000L

    fun select(tasks: List<Task>, preferences: SharedPreferences, now: Long = System.currentTimeMillis()): FocusSelection {
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfTomorrow = Calendar.getInstance().apply {
            timeInMillis = startOfToday
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val today = tasks.filter { it.dueAt != null && it.dueAt!! in startOfToday until startOfTomorrow }
        val current = today.filter { task ->
            task.allDay || (task.dueAt!! <= now && task.dueAt!! + HOUR >= now)
        }.minByOrNull { it.dueAt ?: Long.MAX_VALUE }
        val pinnedTask = preferences.getLong(PINNED_FOCUS_ID, -1L).takeIf { it != -1L }?.let { id -> tasks.firstOrNull { it.id == id } }
        if (pinnedTask == null && preferences.contains(PINNED_FOCUS_ID)) preferences.edit().remove(PINNED_FOCUS_ID).apply()
        val focus = pinnedTask ?: current
        val next = tasks.asSequence()
            .filter { it.id != focus?.id && it.dueAt != null && it.dueAt!! >= now }
            .minByOrNull { it.dueAt!! }
        return FocusSelection(focus, next, pinned = pinnedTask != null)
    }

    fun setPinned(preferences: SharedPreferences, taskId: Long?) {
        if (taskId == null) preferences.edit().remove(PINNED_FOCUS_ID).apply()
        else preferences.edit().putLong(PINNED_FOCUS_ID, taskId).apply()
    }
}
