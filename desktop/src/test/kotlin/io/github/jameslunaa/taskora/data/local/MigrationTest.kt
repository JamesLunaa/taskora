package io.github.jameslunaa.taskora.data.local

import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.jameslunaa.taskora.data.local.entity.toEntity
import io.github.jameslunaa.taskora.domain.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun migrate1To2_makesListIdNullableAndKeepsSubtasks() = runBlocking {
        val file = tmp.root.resolve(TaskoraDatabase.NAME)
        // A version 1 database as the released app left it (schemas/…/1.json).
        BundledSQLiteDriver().open(file.absolutePath).apply {
            VERSION_1.forEach(::execSQL)
            execSQL("INSERT INTO task_lists (id, name, color, sortOrder) VALUES (1, 'Inbox', 0, 0)")
            execSQL(
                "INSERT INTO tasks (id, listId, title, notes, priority, isCompleted, createdAt, sortOrder) " +
                    "VALUES (1, 1, 'a', '', 0, 0, 0, 0)",
            )
            execSQL("INSERT INTO subtasks (id, taskId, title, isCompleted, sortOrder) VALUES (1, 1, 's', 0, 0)")
            execSQL("PRAGMA user_version = 1")
            close()
        }

        val db = TaskoraDatabase.create(file)
        // The tasks table is rebuilt; its subtasks must not cascade away with the old one.
        val task = db.taskDao().getById(1)!!
        assertEquals(1L, task.task.listId)
        assertEquals(listOf("s"), task.subtasks.map { it.title })
        val loose = db.taskDao().insert(Task(listId = null, title = "b").toEntity())
        assertNull(db.taskDao().getById(loose)!!.task.listId)
        assertEquals(1, db.taskListDao().observeAll().first().size)
        db.close()
    }

    private companion object {
        val VERSION_1 = listOf(
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9e1c47837777d5f3ea2b29e9c4fec08e')",
            "CREATE TABLE IF NOT EXISTS `task_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`color` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `sortMode` TEXT NOT NULL DEFAULT 'CREATED', " +
                "`sortDescending` INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `notes` TEXT NOT NULL, `dueDate` INTEGER, `dueTime` INTEGER, `reminderAt` INTEGER, " +
                "`priority` INTEGER NOT NULL, `recurrenceFrequency` TEXT, `recurrenceInterval` INTEGER, `recurrenceDays` INTEGER, " +
                "`isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, `createdAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_dueDate` ON `tasks` (`dueDate`)",
            "CREATE TABLE IF NOT EXISTS `subtasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)",
        )
    }
}
