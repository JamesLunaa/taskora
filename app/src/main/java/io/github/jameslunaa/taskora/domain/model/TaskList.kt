package io.github.jameslunaa.taskora.domain.model

data class TaskList(
    val id: Long = 0,
    val name: String,
    /** ARGB color. */
    val color: Int,
    val sortOrder: Int = 0,
    val sortMode: TaskSortMode = TaskSortMode.CREATED,
    /** Reverses [sortMode]; ignored for [TaskSortMode.MANUAL]. */
    val sortDescending: Boolean = false,
    val activeTaskCount: Int = 0,
) {
    companion object {
        /** Virtual list that shows every list's tasks, grouped by list. Not stored. */
        const val ALL_TASKS_ID = -1L
        // "Blue" from the category palette (ui/theme/Color.kt).
        const val DEFAULT_COLOR = 0xFF5B8DEF.toInt()
    }
}
