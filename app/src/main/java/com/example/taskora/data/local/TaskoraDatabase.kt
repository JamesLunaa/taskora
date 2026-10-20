package com.example.taskora.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.taskora.data.local.dao.SubtaskDao
import com.example.taskora.data.local.dao.TaskDao
import com.example.taskora.data.local.dao.TaskListDao
import com.example.taskora.data.local.entity.SubtaskEntity
import com.example.taskora.data.local.entity.TaskEntity
import com.example.taskora.data.local.entity.TaskListEntity
import com.example.taskora.domain.model.TaskList

@Database(
    entities = [TaskListEntity::class, TaskEntity::class, SubtaskEntity::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // task_lists.sortMode
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
