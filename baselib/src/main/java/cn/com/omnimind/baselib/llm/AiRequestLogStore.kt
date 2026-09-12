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

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .create()
    private val listType = object : TypeToken<List<AiRequestLogEntry>>() {}.type

    @Synchronized
    fun append(entry: AiRequestLogEntry) {
        val mmkv = MMKV.defaultMMKV() ?: return
        val current = readEntriesLocked(mmkv)
        val updated = buildList {
            add(entry)
            current.forEach { existing ->
                if (existing.id != entry.id) {
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
