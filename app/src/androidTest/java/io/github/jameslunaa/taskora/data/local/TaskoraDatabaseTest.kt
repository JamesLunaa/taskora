package io.github.jameslunaa.taskora.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jameslunaa.taskora.data.local.entity.SubtaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskListEntity
import io.github.jameslunaa.taskora.data.local.entity.toDomain
import io.github.jameslunaa.taskora.data.local.entity.toEntity
import io.github.jameslunaa.taskora.domain.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TaskoraDatabaseTest {
    private lateinit var db: TaskoraDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TaskoraDatabase::class.java).build()
        runBlocking { db.taskListDao().insert(TaskListEntity(id = INBOX_ID, name = "Inbox", color = 0, sortOrder = 0)) }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertTask(listId: Long?, title: String, completed: Boolean = false): Long =
        db.taskDao().insert(
            Task(listId = listId, title = title, isCompleted = completed, createdAt = Instant.EPOCH).toEntity(),
        )

    @Test
    fun newDatabase_hasNoLists() = runBlocking {
        val fresh = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TaskoraDatabase::class.java)
            .build()
        assertTrue(fresh.taskListDao().observeAll().first().isEmpty())
        fresh.close()
    }

    @Test
    fun tasksInNoList_haveTheirOwnOrderAndClearCompleted() = runBlocking {
        insertTask(null, "loose")
        insertTask(null, "loose done", completed = true)
        insertTask(INBOX_ID, "listed done", completed = true)
        assertEquals(1, db.taskDao().nextSortOrder(null)) // "= NULL" would match nothing and give 0

        db.taskDao().deleteCompleted(null)

        assertEquals(listOf("loose", "listed done"), db.taskDao().observeAll().first().map { it.task.title })
    }

    @Test
    fun activeCount_includesTasksInNoList() = runBlocking {
        insertTask(INBOX_ID, "a")
        insertTask(null, "loose")
        insertTask(null, "done", completed = true)
        assertEquals(2, db.taskDao().observeActiveCount().first())
    }

    @Test
    fun activeTaskCount_ignoresCompleted() = runBlocking {
        insertTask(INBOX_ID, "a")
        insertTask(INBOX_ID, "b")
        insertTask(INBOX_ID, "done", completed = true)
        assertEquals(2, db.taskListDao().observeAll().first().single().activeTaskCount)
    }

    @Test
    fun deletingList_cascadesToTasksAndSubtasks() = runBlocking {
        val listId = db.taskListDao().insert(TaskListEntity(name = "Work", color = 0, sortOrder = 1))
        val taskId = insertTask(listId, "a")
        db.subtaskDao().insert(SubtaskEntity(taskId = taskId, title = "s", isCompleted = false, sortOrder = 0))

        db.taskListDao().deleteById(listId)

        assertNull(db.taskDao().getById(taskId))
        assertTrue(db.taskDao().observeByList(listId).first().isEmpty())
    }

    @Test
    fun subtaskSortOrders_setTheirOrder() = runBlocking {
        val taskId = insertTask(INBOX_ID, "a")
        val (a, b, c) = listOf("A", "B", "C").mapIndexed { i, title ->
            db.subtaskDao().insert(SubtaskEntity(taskId = taskId, title = title, isCompleted = false, sortOrder = i))
        }
        db.subtaskDao().setSortOrders(listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), db.taskDao().getById(taskId)!!.toDomain().subtasks.map { it.title })
    }

    @Test
    fun insertWithSubtasks_restoresOriginalIds() = runBlocking {
        val taskId = insertTask(INBOX_ID, "a")
        db.subtaskDao().insert(SubtaskEntity(taskId = taskId, title = "s", isCompleted = true, sortOrder = 0))
        val original = db.taskDao().getById(taskId)!!

        db.taskDao().deleteById(taskId)
        db.taskDao().insertWithSubtasks(original.task, original.subtasks)

        assertEquals(original, db.taskDao().getById(taskId))
    }

    @Test
    fun deleteCompleted_onlyAffectsThatList() = runBlocking {
        val otherList = db.taskListDao().insert(TaskListEntity(name = "Other", color = 0, sortOrder = 1))
        insertTask(INBOX_ID, "keep")
        insertTask(INBOX_ID, "done", completed = true)
        insertTask(otherList, "other done", completed = true)

        db.taskDao().deleteCompleted(INBOX_ID)

        assertEquals(listOf("keep"), db.taskDao().observeByList(INBOX_ID).first().map { it.task.title })
        assertEquals(1, db.taskDao().observeByList(otherList).first().size)
    }

    private companion object {
        const val INBOX_ID = 1L
    }
}
