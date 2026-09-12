package cn.com.omnimind.bot.omniflow

import android.content.Context
import cn.com.omnimind.baselib.runlog.Action
import cn.com.omnimind.baselib.runlog.State
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun mapValue(value: Any?): Map<String, Any?> =
    when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().apply {
            value.forEach { (key, item) -> if (key != null) put(key.toString(), item) }
        }
        else -> emptyMap()
    }

internal fun intValue(vararg values: Any?, defaultValue: Int): Int {
    values.forEach { value ->
        when (value) {
            is Number -> return value.toInt()
            is String -> value.trim().toIntOrNull()?.let { return it }
        }
    }
    return defaultValue
}

internal fun firstText(vararg values: Any?): String {
    values.forEach { value ->
        val text = value?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) return text
    }
    return ""
}

internal fun blocksPaymentConfirmation(state: State, action: Action): Boolean {
    if (action.tool !in setOf("click", "long_press", "input_text", "swipe")) return false
    val normalized = state.xml.lowercase().replace(Regex("\\s+"), " ")
    return PAYMENT_CONFIRMATION_MARKERS.any(normalized::contains)
}

private val PAYMENT_CONFIRMATION_MARKERS = setOf(
    "确认支付",
    "立即支付",
    "去支付",
    "支付密码",
    "付款密码",
    "收银台",
    "pay now",
    "confirm payment",
    "payment password",
)

internal fun jsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Map<*, *> -> JsonObject(
        value.entries.associate { (key, item) -> key.toString() to jsonValue(item) },
    )
    is List<*> -> JsonArray(value.map(::jsonValue))
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}

internal fun omniFlowInternalRoot(context: Context): File = File(
    context.applicationInfo.dataDir,
    "workspace/.omnibot",
)
