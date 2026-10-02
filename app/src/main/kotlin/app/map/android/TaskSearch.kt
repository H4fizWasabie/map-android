package app.map.android

/** Every space-separated term must match; a `#term` matches only tags, other terms match title, notes, or tags. */
object TaskSearch {
    fun matches(task: Task, query: String): Boolean {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val tags = task.tags.lowercase().split(',', ' ').filter { it.isNotBlank() }
        return terms.all { term ->
            if (term.startsWith("#")) term.length > 1 && tags.any { it.startsWith(term.substring(1)) }
            else task.title.lowercase().contains(term) || task.notes.lowercase().contains(term) || task.tags.lowercase().contains(term)
        }
    }
}
