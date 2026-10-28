package io.github.jameslunaa.taskora.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.jameslunaa.taskora.data.local.dao.SubtaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskListDao
import io.github.jameslunaa.taskora.data.local.entity.SubtaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskListEntity
import io.github.jameslunaa.taskora.domain.model.TaskList
import kotlinx.coroutines.Dispatchers
import java.io.File

// Same tables as the Android app, but the desktop schema has its own version history (desktop/schemas).
@Database(
    entities = [TaskListEntity::class, TaskEntity::class, SubtaskEntity::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class TaskoraDatabase : RoomDatabase() {
    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao

    /** Seeds the Inbox list when the database file is first created. */
    class SeedCallback(private val inboxName: String) : Callback() {
        override fun onCreate(connection: SQLiteConnection) {
            connection.prepare("INSERT INTO task_lists (id, name, color, sortOrder) VALUES (?, ?, ?, 0)").use {
                it.bindLong(1, TaskList.INBOX_ID)
                it.bindText(2, inboxName)
                it.bindLong(3, TaskList.DEFAULT_COLOR.toLong())
                it.step()
            }
        }
    }

    companion object {
        const val NAME = "taskora.db"

        /** Opens (or creates) the database at [file], or an in-memory one when [file] is null (tests). */
        fun create(file: File?, inboxName: String): TaskoraDatabase {
            val builder = if (file == null) {
                Room.inMemoryDatabaseBuilder<TaskoraDatabase>()
            } else {
                file.parentFile?.mkdirs()
                Room.databaseBuilder<TaskoraDatabase>(name = file.absolutePath)
            }
            return builder
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(SeedCallback(inboxName))
                .build()
        }
    }
}
