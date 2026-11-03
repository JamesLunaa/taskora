package io.github.jameslunaa.taskora.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class MessagesTest {
    private fun decode(json: String) = Wire.decode(json.toByteArray())

    private val task = WireTask(
        id = 42, listId = 1, title = "Water plants", dueDate = LocalDate.of(2026, 11, 2), dueTime = LocalTime.of(9, 0),
        reminderAt = Instant.parse("2026-11-02T08:50:00Z"), priority = WirePriority.HIGH,
        recurrence = WireRecurrence(WireFrequency.WEEKLY, 1, listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
        createdAt = Instant.parse("2026-10-30T12:00:00Z"), subtasks = listOf(WireSubtask(7, "Balcony", true)),
    )

    @Test
    fun roundTrips_withTypeNamesFromTheSpec() {
        val message = AddTask(5, "k1", null, task)
        val json = Wire.encode(message).toString(Charsets.UTF_8)
        assertTrue(json, json.contains("\"type\":\"add_task\""))
        assertTrue(json, json.contains("\"dueTime\":\"09:00\""))
        assertTrue(json, json.contains("\"daysOfWeek\":[\"MONDAY\",\"THURSDAY\"]"))
        assertEquals(Decoded.Valid(message), Wire.decode(Wire.encode(message)))
        assertEquals(Decoded.Valid(Changed), decode("""{"type":"changed"}"""))
    }

    @Test
    fun unknownFields_areIgnored() {
        assertEquals(Decoded.Valid(GetLists(2)), decode("""{"type":"get_lists","id":2,"since":"later"}"""))
    }

    @Test
    fun malformed_unknown_and_invalid_areTold_apart() {
        assertTrue(decode("not json") is Decoded.Malformed)
        assertTrue(decode("""{"id":1}""") is Decoded.Malformed)
        assertEquals(Decoded.Unknown("sync_all", 3), decode("""{"type":"sync_all","id":3}"""))
        // Missing field, wrong type, unknown enum value, bad date.
        assertTrue(decode("""{"type":"get_tasks","id":4}""") is Decoded.Invalid)
        assertTrue(decode("""{"type":"set_completed","id":4,"taskId":"x","completed":true}""") is Decoded.Invalid)
        assertTrue(decode("""{"type":"add_task","id":5,"key":"k","task":{"title":"A","priority":"URGENT"}}""") is Decoded.Invalid)
        assertTrue(decode("""{"type":"add_task","id":5,"key":"k","task":{"title":"A","dueDate":"2026-13-01"}}""") is Decoded.Invalid)
        assertEquals(5L, (decode("""{"type":"add_task","id":5,"key":"k","task":{"title":"A","dueDate":"x"}}""") as Decoded.Invalid).id)
    }

    @Test
    fun limits_areChecked() {
        fun add(task: WireTask) = Wire.decode(Wire.encode(AddTask(1, "k", null, task)))
        assertTrue(add(task.copy(title = "x".repeat(501))) is Decoded.Invalid)
        assertTrue(add(task.copy(title = " ")) is Decoded.Invalid)
        assertTrue(add(task.copy(notes = "x".repeat(20_001))) is Decoded.Invalid)
        assertTrue(add(task.copy(subtasks = List(201) { WireSubtask(title = "s") })) is Decoded.Invalid)
        assertTrue(add(task.copy(recurrence = WireRecurrence(WireFrequency.DAILY, 100))) is Decoded.Invalid)
        assertTrue(Wire.decode(Wire.encode(PairNonce("XYZ"))) is Decoded.Invalid)
        assertTrue(add(task) is Decoded.Valid)
    }
}
