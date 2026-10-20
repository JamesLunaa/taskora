package com.example.taskora.di

import com.example.taskora.data.repository.OfflineTaskListRepository
import com.example.taskora.data.repository.OfflineTaskRepository
import com.example.taskora.data.repository.TaskListRepository
import com.example.taskora.data.repository.TaskRepository
import com.example.taskora.reminders.AlarmReminderScheduler
import com.example.taskora.reminders.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindTaskListRepository(impl: OfflineTaskListRepository): TaskListRepository

    @Binds
    abstract fun bindTaskRepository(impl: OfflineTaskRepository): TaskRepository

    @Binds
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler
}
