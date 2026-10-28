package io.github.jameslunaa.taskora.reminders

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class TrayNotifierTest {
    private fun content(title: String) = NotificationContent(title, "", "Complete", "Snooze", "Open")

    @Test
    fun clickOnMessage_opensTask() = runTest {
        val notifier = TrayNotifier(isAvailable = true, clickWindow = Duration.ofSeconds(15))
        val posted = mutableListOf<String>()
        backgroundScope.async { notifier.messages.collect { posted += it.title } }
        runCurrent()

        val result = async { notifier.show(content("Water plants")) }
        runCurrent()
        assertEquals(listOf("Water plants"), posted)
        assertTrue(notifier.onTrayAction())
        assertEquals(NotificationAction.OPEN, result.await())
        // The click is used up.
        assertFalse(notifier.onTrayAction())
    }

    @Test
    fun noClick_endsWithoutAction() = runTest {
        val notifier = TrayNotifier(isAvailable = true, clickWindow = Duration.ofSeconds(15))
        val result = async { notifier.show(content("Call mum")) }
        advanceTimeBy(16_000)
        assertNull(result.await())
        assertFalse(notifier.onTrayAction())
    }

    @Test
    fun newerMessage_takesTheClick() = runTest {
        val notifier = TrayNotifier(isAvailable = true)
        val first = async { notifier.show(content("First")) }
        runCurrent()
        val second = async { notifier.show(content("Second")) }
        runCurrent()
        assertTrue(notifier.onTrayAction())
        assertNull(first.await())
        assertEquals(NotificationAction.OPEN, second.await())
    }
}
