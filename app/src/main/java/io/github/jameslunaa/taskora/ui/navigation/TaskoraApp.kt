package io.github.jameslunaa.taskora.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.ui.search.SearchScreen
import io.github.jameslunaa.taskora.ui.settings.SettingsScreen
import io.github.jameslunaa.taskora.ui.taskdetail.TaskDetailScreen
import io.github.jameslunaa.taskora.ui.tasks.TaskListScreen
import kotlinx.serialization.Serializable

// Property names double as SavedStateHandle keys in the ViewModels.
@Serializable
data class TaskListRoute(val listId: Long = TaskList.INBOX_ID)

@Serializable
data class TaskDetailRoute(val taskId: Long)

@Serializable
data object SearchRoute

@Serializable
data object SettingsRoute

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
                onSearch = { navController.navigate(SearchRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenList = { listId ->
                    navController.navigate(TaskListRoute(listId)) {
                        popUpTo<TaskListRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.navigateUp() })
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = { navController.navigateUp() },
                onTaskClick = { navController.navigate(TaskDetailRoute(it)) },
            )
        }
        composable<TaskDetailRoute>(
            deepLinks = listOf(navDeepLink<TaskDetailRoute>(basePath = TASK_DEEP_LINK_BASE)),
        ) {
            TaskDetailScreen(onBack = { navController.navigateUp() })
        }
    }
}
