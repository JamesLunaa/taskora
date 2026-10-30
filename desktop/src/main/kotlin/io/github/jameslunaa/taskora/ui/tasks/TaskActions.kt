package io.github.jameslunaa.taskora.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import io.github.jameslunaa.taskora.ui.icons.automirrored.outlined.DriveFileMove
import io.github.jameslunaa.taskora.ui.icons.automirrored.outlined.OpenInNew
import io.github.jameslunaa.taskora.ui.icons.filled.Flag
import io.github.jameslunaa.taskora.ui.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import io.github.jameslunaa.taskora.ui.icons.outlined.Event
import io.github.jameslunaa.taskora.ui.icons.outlined.EventBusy
import io.github.jameslunaa.taskora.ui.icons.outlined.RadioButtonUnchecked
import io.github.jameslunaa.taskora.ui.icons.outlined.SelectAll
import io.github.jameslunaa.taskora.ui.icons.outlined.Today
import io.github.jameslunaa.taskora.ui.icons.outlined.OutlinedFlag
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberCursorPositionProvider
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.delete
import io.github.jameslunaa.taskora.resources.mark_complete
import io.github.jameslunaa.taskora.resources.mark_incomplete
import io.github.jameslunaa.taskora.resources.move_to_list
import io.github.jameslunaa.taskora.resources.open_task
import io.github.jameslunaa.taskora.resources.pick_date
import io.github.jameslunaa.taskora.resources.remove_due_date
import io.github.jameslunaa.taskora.resources.select
import io.github.jameslunaa.taskora.resources.set_priority
import io.github.jameslunaa.taskora.resources.today
import io.github.jameslunaa.taskora.resources.tomorrow
import io.github.jameslunaa.taskora.ui.common.color
import io.github.jameslunaa.taskora.ui.common.label
import io.github.jameslunaa.taskora.ui.theme.listColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate

/** What the right-click menu and the selection bar can do; each call takes the tasks it applies to. */
class TaskBulkActions(
    val onOpen: (Task) -> Unit,
    val onSelect: (Task) -> Unit,
    val onSetDueDate: (Collection<Long>, LocalDate?) -> Unit,
    val onSetPriority: (Collection<Long>, Priority) -> Unit,
    val onMove: (Collection<Long>, Long) -> Unit,
    val onSetCompleted: (Collection<Long>, Boolean) -> Unit,
    val onDelete: (List<Task>) -> Unit,
)

private enum class MenuPage { MAIN, PRIORITY, MOVE }

/**
 * The desktop's version of the phone's long-press sheet, at the pointer. [tasks] is the clicked
 * task, or the whole selection when the clicked task is part of it.
 */
@Composable
fun TaskContextMenu(
    tasks: List<Task>,
    lists: List<TaskList>,
    actions: TaskBulkActions,
    /** Opens the date picker for these tasks. */
    onPickDate: (Collection<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(MenuPage.MAIN) }
    val ids = tasks.map { it.id }
    val single = tasks.singleOrNull()
    fun act(block: () -> Unit) {
        block()
        onDismiss()
    }
    Popup(
        popupPositionProvider = rememberCursorPositionProvider(),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                when (page) {
                    MenuPage.MAIN -> {
                        if (single != null) {
                            Item(Icons.AutoMirrored.Outlined.OpenInNew, Res.string.open_task) { act { actions.onOpen(single) } }
                            Item(Icons.Outlined.SelectAll, Res.string.select) { act { actions.onSelect(single) } }
                            HorizontalDivider()
                        }
                        Item(Icons.Outlined.Today, Res.string.today) { act { actions.onSetDueDate(ids, LocalDate.now()) } }
                        Item(Icons.Outlined.Event, Res.string.tomorrow) {
                            act { actions.onSetDueDate(ids, LocalDate.now().plusDays(1)) }
                        }
                        Item(Icons.Outlined.CalendarMonth, Res.string.pick_date) { act { onPickDate(ids) } }
                        if (tasks.any { it.dueDate != null }) {
                            Item(Icons.Outlined.EventBusy, Res.string.remove_due_date) { act { actions.onSetDueDate(ids, null) } }
                        }
                        HorizontalDivider()
                        Item(Icons.Outlined.OutlinedFlag, Res.string.set_priority) { page = MenuPage.PRIORITY }
                        Item(Icons.AutoMirrored.Outlined.DriveFileMove, Res.string.move_to_list) { page = MenuPage.MOVE }
                        val allCompleted = tasks.all { it.isCompleted }
                        Item(
                            if (allCompleted) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle,
                            if (allCompleted) Res.string.mark_incomplete else Res.string.mark_complete,
                        ) { act { actions.onSetCompleted(ids, !allCompleted) } }
                        HorizontalDivider()
                        Item(Icons.Outlined.Delete, Res.string.delete, destructive = true) { act { actions.onDelete(tasks) } }
                    }
                    MenuPage.PRIORITY -> Priority.entries.reversed().forEach { priority ->
                        DropdownMenuItem(
                            text = { Text(stringResource(priority.label)) },
                            leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null, tint = priority.color()) },
                            onClick = { act { actions.onSetPriority(ids, priority) } },
                        )
                    }
                    MenuPage.MOVE -> lists.forEach { list ->
                        DropdownMenuItem(
                            text = { Text(list.name) },
                            leadingIcon = {
                                Box(Modifier.size(24.dp)) {
                                    Box(Modifier.size(12.dp).background(listColor(list.color), CircleShape))
                                }
                            },
                            enabled = tasks.any { it.listId != list.id },
                            onClick = { act { actions.onMove(ids, list.id) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Item(icon: ImageVector, label: StringResource, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(stringResource(label), color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
    )
}
