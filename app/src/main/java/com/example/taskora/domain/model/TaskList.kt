package com.example.taskora.domain.model

data class TaskList(
    val id: Long = 0,
    val name: String,
    /** ARGB color. */
    val color: Int,
    val sortOrder: Int = 0,
    val sortMode: TaskSortMode = TaskSortMode.CREATED,
    val activeTaskCount: Int = 0,
) {
    val isInbox: Boolean get() = id == INBOX_ID

    companion object {
        /** Created with the database and can't be deleted. */
        const val INBOX_ID = 1L
        const val DEFAULT_COLOR = 0xFF4F5BD5.toInt()
    }
}
