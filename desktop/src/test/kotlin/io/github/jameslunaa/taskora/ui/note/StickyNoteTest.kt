package io.github.jameslunaa.taskora.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.taskora.AppContainer
import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import io.github.jameslunaa.taskora.testing.FakeAutostart
import io.github.jameslunaa.taskora.testing.FakeNotifier
import io.github.jameslunaa.taskora.testing.FakeSettingsRepository
import io.github.jameslunaa.taskora.ui.tasks.TaskListViewModel
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import io.github.jameslunaa.taskora.ui.window.WindowLookActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class StickyNoteTest {
    @get:Rule
    val rule = createComposeRule()

    private val database = TaskoraDatabase.create(file = null, inboxName = "Inbox")
    private val container = AppContainer(database, FakeSettingsRepository(), FakeNotifier(), FakeAutostart())

    private val events = mutableStateListOf<String>()

    @After
    fun tearDown() = database.close()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text), timeoutMillis = 5_000)

    private fun setNote(hintsAvailable: Boolean = true) {
        val viewModel = TaskListViewModel(TaskList.INBOX_ID, container.taskListRepository, container.taskRepository)
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            TaskoraTheme {
                StickyNote(
                    uiState = uiState,
                    look = WindowLook(WindowLevel.ABOVE),
                    hintsAvailable = hintsAvailable,
                    actions = StickyNoteActions(
                        onListChange = { events += "list $it" },
                        onAddTask = viewModel::addTask,
                        onCheckedChange = viewModel::setCompleted,
                        onOpenTask = { events += "open ${it.title}" },
                        onClose = { events += "close" },
                        window = WindowLookActions(
                            onLevelChange = { events += "level $it" },
                            onOpacityChange = {},
                            onOpacityChangeFinished = {},
                        ),
                        onResize = {},
                    ),
                )
            }
        }
    }

    private fun addTask(title: String) {
        rule.onNodeWithText("Add a task").performTextInput(title)
        rule.onNodeWithText(title).performKeyInput { pressKey(Key.Enter) }
        waitFor(title)
    }

    @Test
    fun addsCompletesAndOpensTasks() {
        setNote()
        waitFor("Nothing left to do")
        addTask("Water plants")
        addTask("Call mum")

        rule.onNodeWithText("Call mum").performClick()
        assertEquals(listOf("open Call mum"), events)

        // Completed tasks leave the note.
        rule.onNodeWithText("Water plants").assertExists()
        rule.onNode(isToggleable() and hasParent(hasText("Water plants"))).performClick()
        rule.waitUntilDoesNotExist(hasText("Water plants"), timeoutMillis = 5_000)
    }

    @Test
    fun pickingAList_andTheWindowLevel() {
        setNote()
        waitFor("Inbox")
        rule.onNodeWithContentDescription("Choose list").performClick()
        rule.onNodeWithText("All tasks").performClick()
        rule.onNodeWithContentDescription("Window options").performClick()
        rule.onNodeWithText("Always below").performClick()
        rule.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("list ${TaskList.ALL_TASKS_ID}", "level BELOW", "close"), events)
    }

    @Test
    fun withoutHints_alwaysBelowIsDisabled() {
        setNote(hintsAvailable = false)
        rule.onNodeWithContentDescription("Window options").performClick()
        waitFor("Always below and opacity need an X11 or XWayland session.")
        rule.onNodeWithText("Always below").performClick()
        assertEquals(emptyList<String>(), events)
    }
}
