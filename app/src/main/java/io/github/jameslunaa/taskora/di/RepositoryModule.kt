package io.github.jameslunaa.taskora.di

import io.github.jameslunaa.taskora.data.repository.OfflineTaskListRepository
import io.github.jameslunaa.taskora.data.repository.OfflineTaskRepository
import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.reminders.AlarmReminderScheduler
import io.github.jameslunaa.taskora.reminders.ReminderScheduler
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
