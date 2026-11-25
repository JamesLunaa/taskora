package io.github.jameslunaa.taskora.ui.tasks

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Scrolls [listState] to each task from [addedTaskIds] once it shows up in [sections], unless it's
 * already in full view. A new task can land outside the visible rows: in All tasks it goes above
 * them, and the list keeps its place.
 */
@Composable
fun ScrollToAddedTask(listState: LazyListState, sections: List<TaskSection>, addedTaskIds: Flow<Long>) {
    val currentSections by rememberUpdatedState(sections)
    LaunchedEffect(listState, addedTaskIds) {
        addedTaskIds.collectLatest { id ->
            snapshotFlow { indexOfActiveTask(currentSections, id) }.filterNotNull().first()
            // Let the list lay the new row out first, so the check below sees where it ended up.
            withFrameNanos {}
            val index = indexOfActiveTask(currentSections, id) ?: return@collectLatest
            val layout = listState.layoutInfo
            val end = layout.viewportEndOffset - layout.afterContentPadding
            val inView = layout.visibleItemsInfo.any { it.key == id && it.offset >= 0 && it.offset + it.size <= end }
            if (!inView) listState.animateScrollToItem(index)
        }
    }
}

/** The task's index in the task list's LazyColumn, where each section is its header (if any), then its tasks. */
internal fun indexOfActiveTask(sections: List<TaskSection>, id: Long): Int? {
    var index = 0
    for (section in sections) {
        if (section.list != null) index++
        val position = section.tasks.indexOfFirst { it.id == id }
        if (position >= 0) return index + position
        index += section.tasks.size
    }
    return null
}
