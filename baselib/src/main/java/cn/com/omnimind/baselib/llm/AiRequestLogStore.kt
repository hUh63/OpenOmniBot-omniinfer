package cn.com.omnimind.baselib.llm

import cn.com.omnimind.baselib.util.OmniLog
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class AiRequestLogEntry(
    @field:SerializedName(value = "id", alternate = ["a"])
    val id: String = UUID.randomUUID().toString(),
    @field:SerializedName(value = "createdAt", alternate = ["b"])
    val createdAt: Long = System.currentTimeMillis(),
    @field:SerializedName(value = "label", alternate = ["c"])
    val label: String = "",
    @field:SerializedName(value = "model", alternate = ["d"])
    val model: String = "",
    @field:SerializedName(value = "protocolType", alternate = ["e"])
    val protocolType: String = "openai_compatible",
    @field:SerializedName(value = "url", alternate = ["f"])
    val url: String = "",
    @field:SerializedName(value = "method", alternate = ["g"])
    val method: String = "POST",
    @field:SerializedName(value = "stream", alternate = ["h"])
    val stream: Boolean = false,
    @field:SerializedName(value = "statusCode", alternate = ["i"])
    val statusCode: Int? = null,
    @field:SerializedName(value = "success", alternate = ["j"])
    val success: Boolean = true,
    @field:SerializedName(value = "requestJson", alternate = ["k"])
    val requestJson: String = "",
    @field:SerializedName(value = "responseJson", alternate = ["l"])
    val responseJson: String = "",
    @field:SerializedName(value = "errorMessage", alternate = ["m"])
    val errorMessage: String? = null
) {
    fun toMap(): Map<String, Any?> {
        return linkedMapOf(
            "id" to id,
            "createdAt" to createdAt,
            "label" to label,
            "model" to model,
            "protocolType" to protocolType,
            "url" to url,
            "method" to method,
            "stream" to stream,
            "statusCode" to statusCode,
            "success" to success,
            "requestJson" to requestJson,
            "responseJson" to responseJson,
            "errorMessage" to errorMessage
        )
    }
}

object AiRequestLogStore {
    private const val TAG = "AiRequestLogStore"
    private const val KEY_RECENT_AI_REQUEST_LOGS = "recent_ai_request_logs_v1"
    private const val MAX_LOG_COUNT = 10
    /** 单条请求/响应正文的落盘上限。整份 prompt + 完整回复可能几 MB，10 条就能
     *  让 MMKV 的值大到每次读写都要整块 memcpy；排障也不需要全文。 */
    private const val MAX_FIELD_CHARS = 64_000

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .create()
    private val listType = object : TypeToken<List<AiRequestLogEntry>>() {}.type

    @Synchronized
    fun append(entry: AiRequestLogEntry) {
        val mmkv = MMKV.defaultMMKV() ?: return
        // 落盘前脱敏与截断：日志页是为了排障，不该把带 key 的 URL、整段 prompt、
        // 整段回复原样存进明文 MMKV。
        val sanitized = entry.copy(
            url = redactUrl(entry.url),
            requestJson = truncateForStore(entry.requestJson),
            responseJson = truncateForStore(entry.responseJson)
        )
        val current = readEntriesLocked(mmkv)
        val updated = buildList {
            add(sanitized)
            current.forEach { existing ->
                if (existing.id != sanitized.id) {
                    add(existing)
                }
            }
        }.take(MAX_LOG_COUNT)
        mmkv.encode(KEY_RECENT_AI_REQUEST_LOGS, gson.toJson(updated))
    }

    @Synchronized
    fun listRecent(limit: Int = MAX_LOG_COUNT): List<AiRequestLogEntry> {
        val mmkv = MMKV.defaultMMKV() ?: return emptyList()
        val safeLimit = limit.coerceIn(1, MAX_LOG_COUNT)
        return readEntriesLocked(mmkv).take(safeLimit)
    }

    fun prettyJsonOrRaw(raw: String?): String {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return ""
        }
        return runCatching {
            when {
                normalized.startsWith("{") -> JSONObject(normalized).toString(2)
                normalized.startsWith("[") -> JSONArray(normalized).toString(2)
                else -> normalized
            }
        }.getOrElse { normalized }
    }

    fun buildStreamResponseJson(events: List<String>): String {
        if (events.isEmpty()) {
            return ""
        }
        val jsonArray = JSONArray()
        events.forEach { raw ->
            val normalized = raw.trim()
            if (normalized.isEmpty() || normalized == "[DONE]") {
                return@forEach
            }
            jsonArray.put(parseJsonLikeValue(normalized))
        }
        return if (jsonArray.length() == 0) "" else jsonArray.toString(2)
    }

    private fun parseJsonLikeValue(raw: String): Any {
        return runCatching {
            when {
                raw.startsWith("{") -> JSONObject(raw)
                raw.startsWith("[") -> JSONArray(raw)
                else -> raw
            }
        }.getOrElse { raw }
    }

    /** 去掉 URL / query 里的凭据，保留其余部分仍然可用于排障。 */
    private fun redactUrl(url: String): String {
        if (url.isEmpty()) return url
        return url.replace(
            Regex("(key|api_key|apikey|token|access_token|password|secret)=([^&\\s]+)", RegexOption.IGNORE_CASE)
        ) { match -> "${match.groupValues[1]}=***" }
    }

    private fun truncateForStore(value: String): String {
        if (value.length <= MAX_FIELD_CHARS) return value
        val head = MAX_FIELD_CHARS * 3 / 4
        val tail = MAX_FIELD_CHARS - head
        val omitted = value.length - MAX_FIELD_CHARS
        return value.substring(0, head) + "\n…[省略 $omitted 字符]…\n" +
            value.substring(value.length - tail)
    }

    private fun readEntriesLocked(mmkv: MMKV): List<AiRequestLogEntry> {
        val raw = mmkv.decodeString(KEY_RECENT_AI_REQUEST_LOGS)?.trim().orEmpty()
        if (raw.isEmpty()) {
            return emptyList()
        }
        return runCatching {
            gson.fromJson<List<AiRequestLogEntry>>(raw, listType) ?: emptyList()
        }.getOrElse {
            OmniLog.w(TAG, "read logs failed: ${it.message}")
            emptyList()
        }
    }
}
