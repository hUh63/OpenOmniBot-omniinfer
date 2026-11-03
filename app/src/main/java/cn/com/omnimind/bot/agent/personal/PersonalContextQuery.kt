package cn.com.omnimind.bot.agent.personal

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PersonalContextException(
    val code: String,
    override val message: String,
    val capability: String? = null,
) : Exception(message)

internal fun JsonObject.text(key: String, required: Boolean = false): String? {
    val value = this[key]
    require(value == null || value is JsonNull || value is JsonPrimitive && value.isString) { "$key must be a string" }
    val text = (value as? JsonPrimitive)?.contentOrNull?.trim()
    require(!required || !text.isNullOrBlank()) { "$key is required" }
    require(text == null || text.length <= 8_000) { "$key is too long" }
    return text
}

internal fun JsonObject.number(key: String, default: Int): Int {
    if (!containsKey(key)) return default
    return (this[key] as? JsonPrimitive)?.intOrNull ?: throw IllegalArgumentException("$key must be an integer")
}

internal fun JsonObject.flag(key: String, default: Boolean = false): Boolean =
    if (!containsKey(key)) default else
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: throw IllegalArgumentException("$key must be a boolean")

internal data class ContextPage(val limit: Int, val offset: Int) {
    companion object {
        fun from(args: JsonObject): ContextPage {
            val limit = args.number("limit", 50)
            val offset = args.number("offset", 0)
            require(limit in 1..200) { "limit must be between 1 and 200" }
            require(offset in 0..1_000_000) { "offset must be between 0 and 1000000" }
            return ContextPage(limit, offset)
        }
    }

    fun result(source: String, rows: List<Map<String, Any?>>, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val more = rows.size > limit
        return mapOf(
            "success" to true, "source" to source, "items" to rows.take(limit),
            "count" to rows.size.coerceAtMost(limit), "offset" to offset,
            "hasMore" to more, "nextOffset" to if (more) offset + limit else null,
        ) + extra
    }
}

internal data class ContextTimeRange(val start: Long, val end: Long) {
    companion object {
        fun from(args: JsonObject, now: Long = System.currentTimeMillis(), defaultDays: Long = 1): ContextTimeRange {
            fun parse(value: String): Long = try {
                Instant.parse(value).toEpochMilli()
            } catch (_: java.time.format.DateTimeParseException) {
                try { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                catch (_: java.time.DateTimeException) { throw IllegalArgumentException("Time must be an ISO-8601 timestamp") }
            }
            val start = args.text("startAt")?.let(::parse) ?: (now - defaultDays * 86_400_000L)
            val end = args.text("endAt")?.let(::parse) ?: now
            require(start < end) { "endAt must be after startAt" }
            return ContextTimeRange(start, end)
        }
    }
    fun payload() = mapOf("startAt" to Instant.ofEpochMilli(start).toString(), "endAt" to Instant.ofEpochMilli(end).toString())
}

internal fun likePattern(value: String): String = "%${value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"

/** 取消绑定到本次 Provider 查询，不持有会话或另建 Agent 生命周期。 */
internal suspend fun <T> ContentResolver.contextQuery(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    arguments: Array<String>? = null,
    order: String? = null,
    read: (Cursor, () -> Unit) -> T,
): T = suspendCancellableCoroutine { continuation ->
    val signal = CancellationSignal()
    continuation.invokeOnCancellation { signal.cancel() }
    Dispatchers.IO.dispatch(continuation.context, Runnable {
        try {
            signal.throwIfCanceled()
            val cursor = query(uri, projection, selection, arguments, order, signal)
                ?: throw PersonalContextException("provider_unavailable", "The data provider did not return a result.")
            val result = cursor.use { read(it) { signal.throwIfCanceled() } }
            if (continuation.isActive) continuation.resume(result)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    })
}

internal fun Cursor.value(column: String): String? = getColumnIndex(column).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
internal fun Cursor.longValue(column: String): Long? = value(column)?.toLongOrNull()

internal fun Any?.contextJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { it.key.toString() to it.value.contextJson() })
    is Iterable<*> -> JsonArray(map { it.contextJson() })
    else -> JsonPrimitive(toString())
}
