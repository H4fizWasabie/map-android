package app.map.android

import android.content.SharedPreferences
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tiny in-memory SharedPreferences so FocusPicker can be tested without Robolectric. */
private class FakePreferences(private var pinnedId: Long? = null) : SharedPreferences {
    var cleared = false
        private set

    override fun contains(key: String?) = pinnedId != null
    override fun getLong(key: String?, defValue: Long) = pinnedId ?: defValue
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putLong(key: String?, value: Long) = apply { pinnedId = value }
        override fun remove(key: String?) = apply { pinnedId = null; cleared = true }
        override fun apply() = Unit
        override fun commit() = true
        override fun clear() = apply { pinnedId = null }
        override fun putString(key: String?, value: String?) = this
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int) = this
        override fun putFloat(key: String?, value: Float) = this
        override fun putBoolean(key: String?, value: Boolean) = this
    }

    override fun getAll() = emptyMap<String, Any>()
    override fun getString(key: String?, defValue: String?) = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String?, defValue: Int) = defValue
    override fun getFloat(key: String?, defValue: Float) = defValue
    override fun getBoolean(key: String?, defValue: Boolean) = defValue
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

class FocusPickerTest {
    private fun taskAt(id: Long, dueAt: Long?, allDay: Boolean = false): Task = Task(
        id = id,
        title = "Task $id",
        notes = "",
        dueAt = dueAt,
        recurrence = "Does not repeat",
        tags = "",
        completed = false,
        completedAt = null,
        allDay = allDay,
    )

    /** Noon on a fixed date, far from any midnight boundary so day-range math can't be timezone-flaky. */
    private fun noon(): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 10, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun `an all-day task today becomes the focus over a later timed task`() {
        val now = noon().timeInMillis
        val laterToday = noon().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
        val allDayToday = taskAt(1, dueAt = now, allDay = true)
        val laterTimedTask = taskAt(2, dueAt = laterToday, allDay = false)
        val selection = FocusPicker.select(listOf(allDayToday, laterTimedTask), FakePreferences(), now)
        assertEquals(allDayToday, selection.focus)
        assertEquals(laterTimedTask, selection.next)
        assertFalse(selection.pinned)
    }

    @Test
    fun `a pinned task wins even when it is not the current task`() {
        val now = noon().timeInMillis
        val tomorrow = noon().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val current = taskAt(1, dueAt = now, allDay = true)
        val pinned = taskAt(2, dueAt = tomorrow, allDay = true)
        val selection = FocusPicker.select(listOf(current, pinned), FakePreferences(pinnedId = 2L), now)
        assertEquals(pinned, selection.focus)
        assertTrue(selection.pinned)
    }

    @Test
    fun `a stale pinned id that no longer matches a task is cleared`() {
        val now = noon().timeInMillis
        val preferences = FakePreferences(pinnedId = 99L)
        val selection = FocusPicker.select(listOf(taskAt(1, dueAt = now, allDay = true)), preferences, now)
        assertFalse(selection.pinned)
        assertTrue(preferences.cleared)
    }

    @Test
    fun `with nothing scheduled there is neither a focus nor a next`() {
        val selection = FocusPicker.select(emptyList(), FakePreferences(), noon().timeInMillis)
        assertNull(selection.focus)
        assertNull(selection.next)
    }
}
