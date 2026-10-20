package com.example.taskora.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.taskora.data.repository.TaskRepository
import com.example.taskora.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the widget when tasks change. A running Glance session already follows the data,
 * but sessions end after a short while, so later changes need an explicit update.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            taskRepository.observeAllTasks()
                // Only open tasks with a due date can appear on the widget.
                .map { tasks -> tasks.filter { !it.isCompleted && it.dueDate != null } }
                .distinctUntilChanged()
                .drop(1)
                .collect { TodayWidget().updateAll(context) }
        }
    }
}
