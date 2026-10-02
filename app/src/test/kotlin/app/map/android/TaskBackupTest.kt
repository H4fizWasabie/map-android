package app.map.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskBackupTest {
    private val open = Task(1, "Pay rent", "bank", 1_700_000_000_000, "Monthly", "money", false, null, allDay = false)
    private val done = Task(2, "Call mum", "", null, "", "", true, 1_690_000_000_000)

    @Test
    fun roundTripKeepsEveryField() {
        val parsed = TaskBackup.parse(TaskBackup.toJson(listOf(open, done), exportedAt = 5))
        assertEquals(listOf(open, done).map { it.copy(id = 0) }, parsed)
    }

    @Test
    fun keyIgnoresIdButSeparatesContent() {
        assertEquals(TaskBackup.key(open), TaskBackup.key(open.copy(id = 9)))
        assertNotEquals(TaskBackup.key(open), TaskBackup.key(open.copy(completed = true)))
        assertNotEquals(TaskBackup.key(open), TaskBackup.key(open.copy(notes = "other")))
        assertNotEquals(TaskBackup.key(open), TaskBackup.key(open.copy(tags = "other")))
    }

    @Test
    fun rejectsGarbageAndForeignJson() {
        assertThrows(TaskBackup.InvalidBackup::class.java) { TaskBackup.parse("not json") }
        assertThrows(TaskBackup.InvalidBackup::class.java) { TaskBackup.parse("""{"format":"other","version":1,"tasks":[]}""") }
    }

    @Test
    fun rejectsNewerVersionAndBadTasks() {
        assertThrows(TaskBackup.InvalidBackup::class.java) { TaskBackup.parse("""{"format":"map-tasks","version":99,"tasks":[]}""") }
        assertThrows(TaskBackup.InvalidBackup::class.java) { TaskBackup.parse("""{"format":"map-tasks","version":1,"tasks":[{"title":" "}]}""") }
        assertThrows(TaskBackup.InvalidBackup::class.java) { TaskBackup.parse("""{"format":"map-tasks","version":1,"tasks":[{"notes":"no title"}]}""") }
    }
}
