package io.github.jameslunaa.taskora.domain.model

/** Where a window sits in the stack: pinned over other windows, like any other window, or under them. */
enum class WindowLevel { ABOVE, NORMAL, BELOW }

/** The two windows Taskora can show. */
enum class AppWindow { MAIN, NOTE }

data class WindowLook(
    val level: WindowLevel = WindowLevel.NORMAL,
    /** [MIN_OPACITY]..1. */
    val opacity: Float = 1f,
) {
    companion object {
        /** Lower and the window is hard to find again. */
        const val MIN_OPACITY = 0.2f
    }
}

/** Position and size in screen (AWT) units. */
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int)

/** The compact sticky-note window: one list's open tasks. */
data class NoteSettings(
    val isOpen: Boolean = false,
    /** A list id or [TaskList.ALL_TASKS_ID]. */
    val listId: Long = TaskList.INBOX_ID,
    /** Null until the note is first moved or resized. */
    val bounds: WindowBounds? = null,
)
