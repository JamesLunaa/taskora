package io.github.jameslunaa.taskora.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.jameslunaa.taskora.AppContainer
import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
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

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text), timeoutMillis = 5_000)

    private fun waitForNo(text: String) = rule.waitUntilDoesNotExist(hasText(text), timeoutMillis = 5_000)

    @Test
    fun addOpenEditDeleteAndUndo() {
        rule.setContent { TaskoraTheme { TaskoraApp(AppContainer(database), AppShortcuts()) } }

        rule.onNodeWithText("What needs doing?").performTextInput("Buy milk")
        rule.onNodeWithText("Add").performClick()
        waitFor("Buy milk")

        // Opening the task shows the detail pane.
        rule.onNodeWithText("Buy milk").performClick()
        waitFor("Notes")
        rule.onNodeWithText("High").performClick()
        rule.onNodeWithText("High").assertIsSelected()
        rule.onNodeWithText("Tomorrow").performClick()
        waitFor("Add time")

        rule.onNodeWithText("Delete task").performClick()
        waitForNo("Notes")
        waitForNo("Buy milk")
        waitFor("Undo")
        rule.onNodeWithText("Undo").performClick()
        waitFor("Buy milk")
    }

    @Test
    fun createListOpensIt() {
        rule.setContent { TaskoraTheme { TaskoraApp(AppContainer(database), AppShortcuts()) } }
        waitFor("Inbox")

        rule.onNodeWithText("New list").performClick()
        rule.onNodeWithText("Name").performTextInput("Groceries")
        rule.onNodeWithText("Create").performClick()

        // Shown in both the sidebar and the header band.
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Groceries")).fetchSemanticsNodes().size == 2 }
    }
}
