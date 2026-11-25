package io.github.jameslunaa.taskora.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TaskoraDatabase::class.java)

    @Test
    fun migrate1To2_addsSortModeWithDefault() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO task_lists (id, name, color, sortOrder) VALUES (1, 'Inbox', 0, 0)")
        }
        helper.runMigrationsAndValidate(dbName, 2, true).use { db ->
            db.query("SELECT sortMode FROM task_lists WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("CREATED", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrate2To3_addsSortDescendingOff() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO task_lists (id, name, color, sortOrder, sortMode) VALUES (1, 'Inbox', 0, 0, 'TITLE')")
        }
        helper.runMigrationsAndValidate(dbName, 3, true).use { db ->
            db.query("SELECT sortMode, sortDescending FROM task_lists WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("TITLE", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migrate3To4_makesListIdNullableAndKeepsSubtasks() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL("INSERT INTO task_lists (id, name, color, sortOrder) VALUES (1, 'Inbox', 0, 0)")
            db.execSQL(
                "INSERT INTO tasks (id, listId, title, notes, priority, isCompleted, createdAt, sortOrder) " +
                    "VALUES (1, 1, 'a', '', 0, 0, 0, 0)",
            )
            db.execSQL("INSERT INTO subtasks (id, taskId, title, isCompleted, sortOrder) VALUES (1, 1, 's', 0, 0)")
        }
        // The tasks table is rebuilt; its subtasks must not cascade away with the old one.
        helper.runMigrationsAndValidate(dbName, 4, true).use { db ->
            db.query("SELECT COUNT(*) FROM subtasks").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.execSQL(
                "INSERT INTO tasks (id, listId, title, notes, priority, isCompleted, createdAt, sortOrder) " +
                    "VALUES (2, NULL, 'b', '', 0, 0, 0, 0)",
            )
        }
    }
}
