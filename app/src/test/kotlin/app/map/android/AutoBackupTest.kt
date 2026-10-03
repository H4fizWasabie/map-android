package app.map.android

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun dueWhenNeverBackedUpOrAWeekHasPassed() {
        assertTrue(AutoBackup.isDue(0, 1_000))
        assertFalse(AutoBackup.isDue(1_000, 1_000 + 6 * day))
        assertTrue(AutoBackup.isDue(1_000, 1_000 + 7 * day))
    }

    @Test
    fun dueWhenClockMovedBeforeLastBackup() = assertTrue(AutoBackup.isDue(10 * day, 3 * day))

    @Test
    fun fileNameUsesLocalDate() {
        val at = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 3, 12, 0, 0) }.timeInMillis
        assertEquals("map-auto-20261003.json", AutoBackup.fileName(at))
    }

    @Test
    fun onlyOldOwnFilesAreStale() {
        val names = listOf(
            "map-auto-20260901.json", "map-auto-20260908.json", "map-auto-20260915.json",
            "map-auto-20260922.json", "map-auto-20260929.json", "map-auto-20261003.json",
            "map-tasks-20260101.json", "notes.txt", "map-auto-draft.json"
        )
        assertEquals(listOf("map-auto-20260901.json"), AutoBackup.staleNames(names))
    }
}
