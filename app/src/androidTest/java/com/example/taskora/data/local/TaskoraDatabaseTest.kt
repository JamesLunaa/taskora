package com.example.taskora.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.taskora.data.local.entity.SubtaskEntity
import com.example.taskora.data.local.entity.TaskListEntity
import com.example.taskora.data.local.entity.toEntity
import com.example.taskora.domain.model.Task
import com.example.taskora.domain.model.TaskList
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
        db = Room.inMemoryDatabaseBuilder(context, TaskoraDatabase::class.java)
            .addCallback(TaskoraDatabase.SeedCallback("Inbox"))
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertTask(listId: Long, title: String, completed: Boolean = false): Long =
        db.taskDao().insert(
            Task(listId = listId, title = title, isCompleted = completed, createdAt = Instant.EPOCH).toEntity(),
        )

    @Test
    fun inboxIsSeeded() = runBlocking {
        val lists = db.taskListDao().observeAll().first()
        assertEquals(listOf(TaskList.INBOX_ID), lists.map { it.list.id })
        assertEquals("Inbox", lists.single().list.name)
    }

    @Test
    fun activeTaskCount_ignoresCompleted() = runBlocking {
        insertTask(TaskList.INBOX_ID, "a")
        insertTask(TaskList.INBOX_ID, "b")
        insertTask(TaskList.INBOX_ID, "done", completed = true)
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
    fun insertWithSubtasks_restoresOriginalIds() = runBlocking {
        val taskId = insertTask(TaskList.INBOX_ID, "a")
        db.subtaskDao().insert(SubtaskEntity(taskId = taskId, title = "s", isCompleted = true, sortOrder = 0))
        val original = db.taskDao().getById(taskId)!!

        db.taskDao().deleteById(taskId)
        db.taskDao().insertWithSubtasks(original.task, original.subtasks)

        assertEquals(original, db.taskDao().getById(taskId))
    }

    @Test
    fun deleteCompleted_onlyAffectsThatList() = runBlocking {
        val otherList = db.taskListDao().insert(TaskListEntity(name = "Other", color = 0, sortOrder = 1))
        insertTask(TaskList.INBOX_ID, "keep")
        insertTask(TaskList.INBOX_ID, "done", completed = true)
        insertTask(otherList, "other done", completed = true)

        db.taskDao().deleteCompleted(TaskList.INBOX_ID)

        assertEquals(listOf("keep"), db.taskDao().observeByList(TaskList.INBOX_ID).first().map { it.task.title })
        assertEquals(1, db.taskDao().observeByList(otherList).first().size)
    }
}
