package com.example.taskora.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.example.taskora.domain.model.TaskList
import com.example.taskora.ui.taskdetail.TaskDetailScreen
import com.example.taskora.ui.tasks.TaskListScreen
import kotlinx.serialization.Serializable

// Property names double as SavedStateHandle keys in the ViewModels.
@Serializable
data class TaskListRoute(val listId: Long = TaskList.INBOX_ID)

@Serializable
data class TaskDetailRoute(val taskId: Long)

/** Reminder notifications open `taskora://task/{taskId}`. */
const val TASK_DEEP_LINK_BASE = "taskora://task"

@Composable
fun TaskoraApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = TaskListRoute()) {
        composable<TaskListRoute> {
            TaskListScreen(
                onTaskClick = { navController.navigate(TaskDetailRoute(it)) },
                // Switching lists replaces the current list screen instead of stacking up.
                onOpenList = { listId ->
                    navController.navigate(TaskListRoute(listId)) {
                        popUpTo<TaskListRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<TaskDetailRoute>(
            deepLinks = listOf(navDeepLink<TaskDetailRoute>(basePath = TASK_DEEP_LINK_BASE)),
        ) {
            TaskDetailScreen(onBack = { navController.navigateUp() })
        }
    }
}
