package io.github.jameslunaa.taskora.lan

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// The messages of docs/lan.md. Each class's @SerialName is its "type".

const val PROTOCOL_VERSION = 1

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Message {
    /** A problem with a field, or null when the message is valid (see the limits in docs/lan.md). */
    fun problem(): String? = null
}

/** A message that expects exactly one reply with the same [id]. */
sealed interface Request : Message {
    val id: Long
}

/** A reply to the request with the same [id]. */
sealed interface Reply : Message {
    val id: Long
}

@Serializable
@SerialName("hello")
data class Hello(
    override val id: Long,
    val protocol: Int,
    val deviceId: String,
    val name: String,
    val os: String,
    val paired: Boolean,
) : Request, Reply {
    override fun problem() = checkLength("name", name, Limits.NAME)
        ?: checkLength("deviceId", deviceId, 100)
        ?: checkLength("os", os, 20)
}

@Serializable
@SerialName("error")
data class ErrorReply(override val id: Long = 0, val code: String, val message: String? = null) : Reply

@Serializable
@SerialName("ok")
data class OkReply(override val id: Long) : Reply

@Serializable
@SerialName("ping")
data class Ping(override val id: Long) : Request

@Serializable
@SerialName("pong")
data class Pong(override val id: Long) : Reply

@Serializable
@SerialName("get_lists")
data class GetLists(override val id: Long) : Request

@Serializable
@SerialName("lists")
data class ListsReply(override val id: Long, val lists: List<WireList>) : Reply

@Serializable
@SerialName("get_tasks")
data class GetTasks(override val id: Long, val listId: Long, val includeCompleted: Boolean = false) : Request

@Serializable
@SerialName("tasks")
data class TasksReply(override val id: Long, val tasks: List<WireTask>) : Reply

@Serializable
@SerialName("set_completed")
data class SetCompleted(override val id: Long, val taskId: Long, val completed: Boolean) : Request

@Serializable
@SerialName("add_task")
data class AddTask(override val id: Long, val key: String, val listId: Long? = null, val task: WireTask) : Request {
    override fun problem() = checkLength("key", key, 100) ?: task.problem()
}

@Serializable
@SerialName("task_added")
data class TaskAdded(override val id: Long, val taskId: Long) : Reply

@Serializable
@SerialName("subscribe")
data class Subscribe(override val id: Long) : Request

@Serializable
@SerialName("changed")
data object Changed : Message

@Serializable
@SerialName("unpair")
data class Unpair(override val id: Long) : Request

// Pairing (see docs/lan.md, "Pairing"). Only pair_start and pair_token carry an id.

@Serializable
@SerialName("pair_start")
data class PairStart(override val id: Long) : Request

@Serializable
@SerialName("pair_commit")
data class PairCommit(val commitment: String) : Message {
    override fun problem() = checkHex("commitment", commitment)
}

@Serializable
@SerialName("pair_nonce")
data class PairNonce(val nonce: String) : Message {
    override fun problem() = checkHex("nonce", nonce)
}

@Serializable
@SerialName("pair_reveal")
data class PairReveal(val nonce: String) : Message {
    override fun problem() = checkHex("nonce", nonce)
}

@Serializable
@SerialName("pair_confirm")
data object PairConfirm : Message

@Serializable
@SerialName("pair_cancel")
data class PairCancel(val reason: String? = null) : Message {
    override fun problem() = reason?.let { checkLength("reason", it, Limits.NAME) }
}

@Serializable
@SerialName("pair_token")
data class PairToken(override val id: Long, val token: String) : Request {
    override fun problem() = checkHex("token", token)
}

@Serializable
@SerialName("pair_done")
data class PairDone(override val id: Long) : Reply

/** Error codes of `error` replies. */
object ErrorCode {
    const val NOT_PAIRED = "not_paired"
    const val NOT_FOUND = "not_found"
    const val BAD_REQUEST = "bad_request"
    const val UNSUPPORTED = "unsupported"
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val REJECTED = "rejected"
    const val BUSY = "busy"
}

object Limits {
    const val NAME = 500
    const val NOTES = 20_000
    const val SUBTASKS = 200
    const val MAX_INTERVAL = 99
    const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024
}

@Serializable
data class WireList(
    val id: Long,
    val name: String,
    /** ARGB, as stored. */
    val color: Int,
    val activeCount: Int = 0,
)

@Serializable
enum class WirePriority { NONE, LOW, MEDIUM, HIGH }

@Serializable
enum class WireFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

@Serializable
data class WireRecurrence(
    val frequency: WireFrequency,
    val interval: Int = 1,
    val daysOfWeek: List<@Serializable(DayOfWeekSerializer::class) DayOfWeek> = emptyList(),
)

@Serializable
data class WireSubtask(
    val id: Long? = null,
    val title: String,
    val completed: Boolean = false,
)

@Serializable
data class WireTask(
    val id: Long? = null,
    val listId: Long? = null,
    val title: String,
    val notes: String = "",
    @Serializable(LocalDateSerializer::class) val dueDate: LocalDate? = null,
    @Serializable(LocalTimeSerializer::class) val dueTime: LocalTime? = null,
    @Serializable(InstantSerializer::class) val reminderAt: Instant? = null,
    val priority: WirePriority = WirePriority.NONE,
    val recurrence: WireRecurrence? = null,
    val completed: Boolean = false,
    @Serializable(InstantSerializer::class) val completedAt: Instant? = null,
    @Serializable(InstantSerializer::class) val createdAt: Instant? = null,
    val subtasks: List<WireSubtask> = emptyList(),
) {
    fun problem(): String? {
        if (title.isBlank()) return "title is empty"
        checkLength("title", title, Limits.NAME)?.let { return it }
        checkLength("notes", notes, Limits.NOTES)?.let { return it }
        if (subtasks.size > Limits.SUBTASKS) return "more than ${Limits.SUBTASKS} subtasks"
        subtasks.forEach { subtask -> checkLength("subtask title", subtask.title, Limits.NAME)?.let { return it } }
        recurrence?.let {
            if (it.interval !in 1..Limits.MAX_INTERVAL) return "interval must be 1–${Limits.MAX_INTERVAL}"
        }
        if (dueTime != null && dueDate == null) return "dueTime without dueDate"
        return null
    }
}

private fun checkLength(field: String, value: String, max: Int) =
    if (value.length > max) "$field is longer than $max characters" else null

private fun checkHex(field: String, value: String) =
    if (value.length > 128 || value.length % 2 != 0 || !value.all { it in '0'..'9' || it in 'a'..'f' }) "$field isn't hex" else null

/** Decoding a frame: a message, or why it was refused. */
sealed interface Decoded {
    data class Valid(val message: Message) : Decoded

    /** Not JSON, or no string "type": the connection is closed. */
    data class Malformed(val reason: String) : Decoded

    /** A message type this version doesn't know: reply `unsupported`. */
    data class Unknown(val type: String, val id: Long?) : Decoded

    /** A known type with bad fields: reply `bad_request`. */
    data class Invalid(val type: String, val id: Long?, val reason: String) : Decoded
}

object Wire {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val knownTypes: Set<String> =
        Message.serializer().descriptor.getElementDescriptor(1).let { subtypes ->
            (0 until subtypes.elementsCount).map(subtypes::getElementName).toSet()
        }

    fun encode(message: Message): ByteArray = json.encodeToString(Message.serializer(), message).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Decoded {
        val element = runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
            .getOrElse { return Decoded.Malformed("Not a JSON object") }
        val type = (element["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return Decoded.Malformed("No type")
        val id = (element["id"] as? JsonPrimitive)?.content?.toLongOrNull()
        if (type !in knownTypes) return Decoded.Unknown(type, id)
        val message = runCatching { json.decodeFromJsonElement(Message.serializer(), element) }
            .getOrElse { return Decoded.Invalid(type, id, it.message?.lineSequence()?.firstOrNull() ?: "Invalid fields") }
        message.problem()?.let { return Decoded.Invalid(type, id, it) }
        return Decoded.Valid(message)
    }

    /** For tests: what a JSON object decodes to. */
    internal fun decode(json: JsonObject) = decode(json.toString().toByteArray())
}

internal object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

internal object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

internal object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor = PrimitiveSerialDescriptor("DayOfWeek", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DayOfWeek) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): DayOfWeek = DayOfWeek.valueOf(decoder.decodeString())
}
