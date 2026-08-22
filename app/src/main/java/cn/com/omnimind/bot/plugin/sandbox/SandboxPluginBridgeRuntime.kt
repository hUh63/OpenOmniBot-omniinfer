package cn.com.omnimind.bot.plugin.sandbox

import android.content.Context
import cn.com.omnimind.baselib.llm.ChatCompletionMessage
import cn.com.omnimind.baselib.llm.ChatCompletionRequest
import cn.com.omnimind.baselib.llm.ChatCompletionStreamOptions
import cn.com.omnimind.baselib.llm.ChatCompletionThinking
import cn.com.omnimind.baselib.llm.contentText
import cn.com.omnimind.bot.agent.AgentConversationContextCompactor
import cn.com.omnimind.bot.agent.HttpAgentLlmClient
import cn.com.omnimind.bot.plugin.OmniPluginHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SandboxPluginBridgeRuntime private constructor(
    private val appContext: Context?,
    private val pool: SandboxPluginPool,
) {
    constructor(context: Context) : this(
        appContext = context.applicationContext,
        pool = SandboxPluginPool(context.applicationContext),
    )

    internal constructor(pool: SandboxPluginPool) : this(
        appContext = null,
        pool = pool,
    )

    suspend fun invoke(
        pluginId: String,
        method: String,
        params: Map<*, *>,
    ): Map<String, Any?> {
        requireEnabled(pluginId)
        val command = when (method) {
            "db.insert" -> SandboxPluginCommand.Insert(
                pluginId = pluginId,
                table = params.requireString("table"),
                values = params.requireStringMap("values"),
            )
            "db.query" -> SandboxPluginCommand.Query(
                pluginId = pluginId,
                table = params.requireString("table"),
                where = params.optionalStringMap("where"),
                orderBy = params["orderBy"]?.toString()?.trim()?.takeIf(String::isNotEmpty),
                limit = (params["limit"] as? Number)?.toInt() ?: 100,
            )
            "db.update" -> SandboxPluginCommand.Update(
                pluginId = pluginId,
                table = params.requireString("table"),
                id = params.requireValue("id"),
                values = params.requireStringMap("values"),
            )
            "db.delete" -> SandboxPluginCommand.Delete(
                pluginId = pluginId,
                table = params.requireString("table"),
                id = params.requireValue("id"),
            )
            "tool.call" -> return pool.executeDashboardTool(
                pluginId = pluginId,
                toolName = params.requireString("name"),
                arguments = params.optionalStringMap("arguments").toJsonObject(),
            )
            "ai.generate" -> {
                pool.requireAnyPermission(
                    pluginId,
                    setOf(SandboxProjectPermission.XIAOWAN, SandboxProjectPermission.AI),
                )
                return generateWithXiaowan(params)
            }
            else -> throw IllegalArgumentException("Unsupported sandbox method: $method")
        }
        return pool.execute(command).requireSuccess().payload
    }

    private suspend fun requireEnabled(pluginId: String) {
        val context = appContext ?: return
        val state = OmniPluginHost.get(context).list()
            .firstOrNull { it.descriptor.id == pluginId }
            ?: throw IllegalArgumentException("Unknown plugin: $pluginId")
        require(state.installed && state.enabled) {
            "Plugin must be installed and enabled: $pluginId"
        }
    }

    private suspend fun generateWithXiaowan(params: Map<*, *>): Map<String, Any?> {
        val prompt = params.requireString("prompt")
        require(prompt.length <= MAX_AI_PROMPT_CHARS) {
            "prompt exceeds the $MAX_AI_PROMPT_CHARS character limit"
        }
        val system = params["system"]?.toString()?.trim().orEmpty()
        require(system.length <= MAX_AI_SYSTEM_CHARS) {
            "system exceeds the $MAX_AI_SYSTEM_CHARS character limit"
        }
        val maxTokens = ((params["maxTokens"] as? Number)?.toInt() ?: DEFAULT_AI_MAX_TOKENS)
            .coerceIn(MIN_AI_MAX_TOKENS, MAX_AI_MAX_TOKENS)
        val temperature = ((params["temperature"] as? Number)?.toDouble() ?: 0.4)
            .coerceIn(0.0, 2.0)
        val request = buildFastAiRequest(
            prompt = prompt,
            system = system,
            maxTokens = maxTokens,
            temperature = temperature,
        )
        val turn = withContext(Dispatchers.IO) {
            HttpAgentLlmClient(CoroutineScope(currentCoroutineContext())).streamTurn(
                request,
            )
        }
        return mapOf(
            "text" to turn.message.contentText(),
            "model" to turn.resolvedModel,
            "usage" to turn.usage?.let { usage ->
                mapOf(
                    "promptTokens" to usage.promptTokens,
                    "completionTokens" to usage.completionTokens,
                    "totalTokens" to usage.totalTokens,
                )
            },
        )
    }

    private fun Map<*, *>.requireString(key: String): String =
        get(key)?.toString()?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("$key is required")

    private fun Map<*, *>.requireStringMap(key: String): Map<String, Any?> {
        val value = get(key) as? Map<*, *>
            ?: throw IllegalArgumentException("$key must be an object")
        return value.entries.associate { (entryKey, entryValue) ->
            val stringKey = entryKey as? String
                ?: throw IllegalArgumentException("$key contains a non-string key")
            stringKey to entryValue
        }
    }

    private fun Map<*, *>.optionalStringMap(key: String): Map<String, Any?> {
        if (get(key) == null) return emptyMap()
        return requireStringMap(key)
    }

    private fun Map<*, *>.requireValue(key: String): Any =
        get(key) ?: throw IllegalArgumentException("$key is required")

    private fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(
        entries.associate { (key, value) -> key to value.toJsonElement() },
    )

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Map<*, *> -> JsonObject(
            entries.associate { (key, value) ->
                val stringKey = key as? String
                    ?: throw IllegalArgumentException("Tool arguments contain a non-string key")
                stringKey to value.toJsonElement()
            },
        )
        is Iterable<*> -> JsonArray(map { value -> value.toJsonElement() })
        is Array<*> -> JsonArray(map { value -> value.toJsonElement() })
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }

    private companion object {
        const val MIN_AI_MAX_TOKENS = 32
        const val DEFAULT_AI_MAX_TOKENS = 1_024
        const val MAX_AI_MAX_TOKENS = 4_096
        const val MAX_AI_PROMPT_CHARS = 32_000
        const val MAX_AI_SYSTEM_CHARS = 8_000
    }
}

internal fun buildFastAiRequest(
    prompt: String,
    system: String,
    maxTokens: Int,
    temperature: Double,
): ChatCompletionRequest {
    val messages = buildList {
        if (system.isNotEmpty()) {
            add(ChatCompletionMessage(role = "system", content = JsonPrimitive(system)))
        }
        add(ChatCompletionMessage(role = "user", content = JsonPrimitive(prompt)))
    }
    return ChatCompletionRequest(
        messages = messages,
        model = AgentConversationContextCompactor.DEFAULT_AGENT_MODEL_SCENE,
        maxCompletionTokens = maxTokens,
        temperature = temperature,
        stream = true,
        streamOptions = ChatCompletionStreamOptions(),
        reasoningEffort = "none",
        enableThinking = false,
        thinking = ChatCompletionThinking(type = "disabled"),
    )
}
