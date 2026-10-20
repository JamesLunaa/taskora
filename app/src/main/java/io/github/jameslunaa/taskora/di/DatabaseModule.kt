package io.github.jameslunaa.taskora.di

import android.content.Context
import androidx.room.Room
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.data.local.dao.SubtaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TaskoraDatabase =
        Room.databaseBuilder(context, TaskoraDatabase::class.java, TaskoraDatabase.NAME)
            .addCallback(TaskoraDatabase.SeedCallback(context.getString(R.string.inbox)))
            .build()

    @Provides
    fun provideTaskListDao(db: TaskoraDatabase): TaskListDao = db.taskListDao()

    @Provides
    fun provideTaskDao(db: TaskoraDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideSubtaskDao(db: TaskoraDatabase): SubtaskDao = db.subtaskDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
