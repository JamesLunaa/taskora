package io.github.jameslunaa.taskora.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.jameslunaa.taskora.data.local.dao.SubtaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskListDao
import io.github.jameslunaa.taskora.data.local.entity.SubtaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskListEntity
import kotlinx.coroutines.Dispatchers
import java.io.File

// Same tables as the Android app, but the desktop schema has its own version history (desktop/schemas).
@Database(
    entities = [TaskListEntity::class, TaskEntity::class, SubtaskEntity::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // tasks.listId nullable (tasks in no list)
    ],
)
@TypeConverters(Converters::class)
abstract class TaskoraDatabase : RoomDatabase() {
    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao

    companion object {
        const val NAME = "taskora.db"

        /** Opens (or creates) the database at [file], or an in-memory one when [file] is null (tests). */
        fun create(file: File?): TaskoraDatabase {
            val builder = if (file == null) {
                Room.inMemoryDatabaseBuilder<TaskoraDatabase>()
            } else {
                file.parentFile?.mkdirs()
                Room.databaseBuilder<TaskoraDatabase>(name = file.absolutePath)
            }
            return builder
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}
