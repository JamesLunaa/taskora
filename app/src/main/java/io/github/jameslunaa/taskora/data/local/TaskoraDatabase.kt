package io.github.jameslunaa.taskora.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.jameslunaa.taskora.data.local.dao.SubtaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskListDao
import io.github.jameslunaa.taskora.data.local.entity.SubtaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskListEntity
import io.github.jameslunaa.taskora.domain.model.TaskList

@Database(
    entities = [TaskListEntity::class, TaskEntity::class, SubtaskEntity::class],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // task_lists.sortMode
        AutoMigration(from = 2, to = 3), // task_lists.sortDescending
    ],
)
@TypeConverters(Converters::class)
abstract class TaskoraDatabase : RoomDatabase() {
    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao

    /** Seeds the Inbox list when the database file is first created. */
    class SeedCallback(private val inboxName: String) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO task_lists (id, name, color, sortOrder) VALUES (?, ?, ?, 0)",
                arrayOf<Any>(TaskList.INBOX_ID, inboxName, TaskList.DEFAULT_COLOR),
            )
        }
    }

    companion object {
        const val NAME = "taskora.db"
    }
}
