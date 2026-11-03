package io.github.jameslunaa.taskora.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import io.github.jameslunaa.taskora.AppContainer
import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.testing.FakeAutostart
import io.github.jameslunaa.taskora.testing.FakeNotifier
import io.github.jameslunaa.taskora.testing.FakeSettingsRepository
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Drives the whole window against an in-memory database. */
@OptIn(ExperimentalTestApi::class)
class TaskoraAppTest {
    @get:Rule
    val rule = createComposeRule()

    private val database = TaskoraDatabase.create(file = null, inboxName = "Inbox")

    @After
    fun tearDown() = database.close()

    private fun container() = AppContainer(database, FakeSettingsRepository(), FakeNotifier(), FakeAutostart())

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text), timeoutMillis = 5_000)

    /** Today/Tomorrow also label the day filter chips, which are selectable; this skips them. */
    private fun notDayChip(text: String) = hasText(text) and !isSelectable()

    private fun waitForNo(text: String) = rule.waitUntilDoesNotExist(hasText(text), timeoutMillis = 5_000)

    @Test
    fun addOpenEditDeleteAndUndo() {
        rule.setContent { TaskoraTheme { TaskoraApp(container(), AppShortcuts()) } }

        rule.onNodeWithText("What needs doing?").performTextInput("Buy milk")
        rule.onNodeWithText("Add").performClick()
        waitFor("Buy milk")

        // Opening the task shows the detail pane.
        rule.onNodeWithText("Buy milk").performClick()
        waitFor("Notes")
        rule.onNodeWithText("High").performClick()
        rule.onNodeWithText("High").assertIsSelected()
        rule.onNode(notDayChip("Tomorrow")).performClick()
        waitFor("Add time")

        rule.onNodeWithText("Delete task").performScrollTo().performClick()
        waitForNo("Notes")
        waitForNo("Buy milk")
        waitFor("Undo")
        rule.onNodeWithText("Undo").performClick()
        waitFor("Buy milk")
    }

    @Test
    fun subtask_editInline() {
        rule.setContent { TaskoraTheme { TaskoraApp(container(), AppShortcuts()) } }
        addTask("Trip")
        rule.onNodeWithText("Trip").performClick()
        waitFor("Notes")

        rule.onNodeWithText("Add a subtask").performScrollTo().performTextInput("Flights")
        rule.onNode(hasSetTextAction() and hasText("Flights")).performKeyInput { pressKey(Key.Enter) }
        waitFor("Flights")

        // Clicking the title opens an editor; Enter saves.
        rule.onNodeWithText("Flights").performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("Flights")).performTextReplacement("Book flights")
        rule.onNode(hasSetTextAction() and hasText("Book flights")).performKeyInput { pressKey(Key.Enter) }
        waitFor("Book flights")
        rule.waitUntilDoesNotExist(hasSetTextAction() and hasText("Book flights"), timeoutMillis = 5_000)

        // Escape throws the edit away.
        rule.onNodeWithText("Book flights").performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("Book flights")).performTextReplacement("Nope")
        rule.onNode(hasSetTextAction() and hasText("Nope")).performKeyInput { pressKey(Key.Escape) }
        waitFor("Book flights")
        rule.onNode(hasText("Nope")).assertDoesNotExist()
    }

    @Test
    fun dayFilter_staysOnAcrossLists() {
        rule.setContent { TaskoraTheme { TaskoraApp(container(), AppShortcuts()) } }
        addTask("Someday")

        rule.onNodeWithText("Today").performClick()
        waitForNo("Someday")
        waitFor("Nothing due today")

        // Added under the filter, so it's due today and stays in view.
        addTask("Water plants")

        rule.onNodeWithText("New list").performClick()
        rule.onNodeWithText("Name").performTextInput("Groceries")
        rule.onNodeWithText("Create").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Groceries")).fetchSemanticsNodes().size == 2 }
        waitFor("Nothing due today")
        rule.onNodeWithText("Today").assertIsSelected()

        // Clicking the selected chip again turns the filter off.
        rule.onNodeWithText("Today").performClick()
        waitFor("No tasks yet")
    }

    @Test
    fun createListOpensIt() {
        rule.setContent { TaskoraTheme { TaskoraApp(container(), AppShortcuts()) } }
        waitFor("Inbox")

        rule.onNodeWithText("New list").performClick()
        rule.onNodeWithText("Name").performTextInput("Groceries")
        rule.onNodeWithText("Create").performClick()

        // Shown in both the sidebar and the header band.
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Groceries")).fetchSemanticsNodes().size == 2 }
    }

    private fun addTask(title: String) {
        rule.onNodeWithText("What needs doing?").performTextInput(title)
        rule.onNodeWithText("Add").performClick()
        waitFor(title)
    }

    @Test
    fun rightClickMenu_setsDate_andSelects() {
        rule.setContent { TaskoraTheme { TaskoraApp(container(), AppShortcuts()) } }
        addTask("Pay rent")

        rule.onNodeWithText("Pay rent").performMouseInput { rightClick() }
        waitFor("Move to list…")
        rule.onNode(notDayChip("Tomorrow")).performClick()
        // The row's due label, next to the day filter chip.
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Tomorrow")).fetchSemanticsNodes().size == 2 }

        rule.onNodeWithText("Pay rent").performMouseInput { rightClick() }
        rule.onNodeWithText("Select").performClick()
        waitFor("1 selected")
        rule.onNodeWithContentDescription("Mark complete").performClick()
        waitForNo("1 selected")
        waitFor("Completed (1)")
    }

    @Test
    fun search_findsTasksAcrossLists() {
        rule.setContent { TaskoraTheme { TaskoraApp(container(), AppShortcuts()) } }
        addTask("Buy oat milk")
        addTask("Call the bank")

        rule.onNodeWithText("Search").performClick()
        rule.onNodeWithText("Search tasks").performTextInput("milk")
        waitFor("Buy oat milk")
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Call the bank")).fetchSemanticsNodes().isEmpty() }
    }
}
