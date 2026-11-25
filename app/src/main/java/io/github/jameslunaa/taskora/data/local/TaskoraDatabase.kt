package io.github.jameslunaa.taskora.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.jameslunaa.taskora.data.local.dao.SubtaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskListDao
import io.github.jameslunaa.taskora.data.local.entity.SubtaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskListEntity

@Database(
    entities = [TaskListEntity::class, TaskEntity::class, SubtaskEntity::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // task_lists.sortMode
        AutoMigration(from = 2, to = 3), // task_lists.sortDescending
        AutoMigration(from = 3, to = 4), // tasks.listId nullable (tasks in no list)
    ],
)
@TypeConverters(Converters::class)
abstract class TaskoraDatabase : RoomDatabase() {
    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao

    companion object {
        const val NAME = "taskora.db"
    }
}
