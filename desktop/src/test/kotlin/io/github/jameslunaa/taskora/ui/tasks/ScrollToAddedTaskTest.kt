package io.github.jameslunaa.taskora.ui.tasks

import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollToAddedTaskTest {
    private fun task(id: Long) = Task(id = id, listId = null, title = "t$id")

    @Test
    fun indexOfActiveTask_countsSectionHeaders() {
        val sections = listOf(
            TaskSection(null, listOf(task(1), task(2))),
            TaskSection(TaskList(id = 7, name = "Work", color = 0), listOf(task(3), task(4))),
        )
        assertEquals(0, indexOfActiveTask(sections, 1))
        assertEquals(1, indexOfActiveTask(sections, 2))
        // The Work header sits at index 2.
        assertEquals(3, indexOfActiveTask(sections, 3))
        assertEquals(4, indexOfActiveTask(sections, 4))
        assertNull(indexOfActiveTask(sections, 99))
    }
}
