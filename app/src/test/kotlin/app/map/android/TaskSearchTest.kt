package app.map.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSearchTest {
    private val task = Task(1, "Buy Milk", "from the corner shop", null, "", "errands, personal", false, null)

    @Test fun blankQueryMatchesEverything() = assertTrue(TaskSearch.matches(task, "  "))

    @Test fun matchesTitleNotesAndTagsCaseInsensitively() {
        assertTrue(TaskSearch.matches(task, "milk"))
        assertTrue(TaskSearch.matches(task, "CORNER"))
        assertTrue(TaskSearch.matches(task, "personal"))
    }

    @Test fun allTermsMustMatch() {
        assertTrue(TaskSearch.matches(task, "buy shop"))
        assertFalse(TaskSearch.matches(task, "buy bread"))
    }

    @Test fun hashTermMatchesOnlyTags() {
        assertTrue(TaskSearch.matches(task, "#errands"))
        assertTrue(TaskSearch.matches(task, "#pers"))
        assertFalse(TaskSearch.matches(task, "#milk"))
        assertFalse(TaskSearch.matches(task, "#"))
    }
}
