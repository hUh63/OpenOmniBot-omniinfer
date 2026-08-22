package cn.com.omnimind.bot.omniflow

import cn.com.omnimind.baselib.llm.ChatCompletionRequest
import cn.com.omnimind.baselib.llm.ChatCompletionTurn
import cn.com.omnimind.bot.agent.AgentLlmClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun AgentLlmClient.asOmniFlowModelClient(): OmniFlowModelClient =
    object : OmniFlowModelClient {
        override suspend fun streamTurn(
            request: ChatCompletionRequest,
            onReasoningUpdate: (suspend (String) -> Unit)?,
        ): ChatCompletionTurn {
            val turn = this@asOmniFlowModelClient.streamTurn(
                request = request,
                onReasoningUpdate = onReasoningUpdate,
            )
            return turn.adaptQwenVlmCoordinates(request)
        }
    }

/**
 * Qwen-VL emits visual points on its model-native 0..1000 image scale even
 * when the OpenAI-compatible tool schema advertises the original display
 * pixels. Restore the declared pixel contract before OmniFlow performs its
 * single screen-pixel-to-canonical conversion.
 */
internal fun ChatCompletionTurn.adaptQwenVlmCoordinates(
    request: ChatCompletionRequest,
): ChatCompletionTurn {
    val model = resolvedModel.orEmpty().ifBlank { request.model }
    if (!QWEN_VL_MODEL.containsMatchIn(model)) return this
    val toolSchemas = request.tools.associateBy { it.function.name }
    val calls = message.toolCalls ?: return this
    var changed = false
    val adaptedCalls = calls.map { call ->
        val properties = toolSchemas[call.function.name]
            ?.function
            ?.parameters
            ?.get("properties")
            ?.let { it as? JsonObject }
            ?: return@map call
        val coordinateBounds = properties.mapNotNull { (field, schema) ->
            val maximum = (schema as? JsonObject)
                ?.get("maximum")
                ?.let(::numberValue)
                ?.takeIf { it > NORMALIZED_COORDINATE_MAX }
                ?: return@mapNotNull null
            field to maximum
        }.toMap()
        if (coordinateBounds.isEmpty()) return@map call
        val adaptedArguments = adaptArguments(
            arguments = call.function.arguments,
            coordinateBounds = coordinateBounds,
        ) ?: return@map call
        changed = true
        call.copy(function = call.function.copy(arguments = adaptedArguments))
    }
    return if (!changed) this else copy(message = message.copy(toolCalls = adaptedCalls))
}

private fun adaptArguments(
    arguments: String,
    coordinateBounds: Map<String, Double>,
): String? {
    val root = runCatching { JSON.parseToJsonElement(arguments).jsonObject }.getOrNull()
        ?: return null
    val output = root.toMutableMap()
    var changed = false

    coordinateBounds.forEach { (field, maximum) ->
        val value = output[field] ?: return@forEach
        val pair = value.asNumberPair()
        if (pair != null && field.endsWith("1") || pair != null && field == "x") {
            // Qwen's point-array format is [x, y] in normalized image space.
            val xField = if (field == "x") "x" else field
            val yField = if (field == "x") "y" else field.replace("x", "y")
            val yMaximum = coordinateBounds[yField]
            if (yMaximum != null) {
                output[xField] = JsonPrimitive(scale(pair[0], maximum))
                output[yField] = JsonPrimitive(scale(pair[1], yMaximum))
                changed = true
            }
            return@forEach
        }
        val scalar = numberValue(value) ?: return@forEach
        if (scalar in 0.0..NORMALIZED_COORDINATE_MAX) {
            output[field] = JsonPrimitive(scale(scalar, maximum))
            changed = true
        }
    }
    return if (changed) JsonObject(output).toString() else null
}

private fun JsonElement.asNumberPair(): List<Double>? =
    (this as? JsonArray)?.takeIf { it.size == 2 }?.map { numberValue(it) ?: return null }

private fun scale(value: Double, maximum: Double): Double =
    value / NORMALIZED_COORDINATE_MAX * maximum

private fun numberValue(value: JsonElement): Double? =
    (value as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private const val NORMALIZED_COORDINATE_MAX = 1000.0
private val QWEN_VL_MODEL = Regex(
    "(?:^|[^a-z0-9])qwen(?:\\d+(?:\\.\\d+)?)?[-_.]?vl(?:[^a-z0-9]|$)",
    RegexOption.IGNORE_CASE,
)
private val JSON = Json {
    isLenient = true
    ignoreUnknownKeys = true
}
