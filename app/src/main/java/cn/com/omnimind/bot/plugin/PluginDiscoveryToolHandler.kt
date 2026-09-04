package cn.com.omnimind.bot.plugin

import android.content.Context
import cn.com.omnimind.baselib.llm.AssistantToolCall
import cn.com.omnimind.bot.agent.AgentCallback
import cn.com.omnimind.bot.agent.AgentExecutionEnvironment
import cn.com.omnimind.bot.agent.AgentToolExecutionHandle
import cn.com.omnimind.bot.agent.AgentToolRegistry
import cn.com.omnimind.bot.agent.ToolExecutionResult
import cn.com.omnimind.bot.agent.tool.handlers.SharedHelper
import cn.com.omnimind.bot.agent.tool.handlers.ToolHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal object PluginDiscoveryPayloads {
    fun list(
        plugins: List<OmniPluginState>,
        installedOnly: Boolean,
    ): JsonObject {
        val visiblePlugins = plugins.filter { plugin ->
            plugin.isVisibleToUsers() && (!installedOnly || plugin.installed)
        }
        return buildJsonObject {
            put("plugins", buildJsonArray {
                visiblePlugins.forEach { add(it.toDiscoveryJson(includeDescription = false)) }
            })
            put("count", visiblePlugins.size)
            put(
                "install_policy",
                "Plugin installation requires explicit confirmation in the Plugin Market UI.",
            )
        }
    }

    fun get(plugins: List<OmniPluginState>, pluginId: String): JsonObject? =
        plugins.firstOrNull { it.descriptor.id == pluginId && it.isVisibleToUsers() }
            ?.toDiscoveryJson(includeDescription = true)

    private fun OmniPluginState.isVisibleToUsers(): Boolean =
        descriptor.presentation["visibility"]?.jsonPrimitive?.contentOrNull != "hidden"

    private fun OmniPluginState.toDiscoveryJson(includeDescription: Boolean) = buildJsonObject {
        put("id", descriptor.id)
        put("name", descriptor.name)
        put("version", descriptor.version)
        put("publisher", descriptor.publisher)
        put("kind", descriptor.kind.wireName)
        put("installed", installed)
        put("enabled", enabled)
        put("compatible", compatible)
        if (includeDescription) {
            put("description", descriptor.description)
            put("download_size_bytes", descriptor.downloadSizeBytes)
            put("capabilities", buildJsonArray {
                descriptor.capabilities.forEach { add(JsonPrimitive(it)) }
            })
            errorMessage?.let { put("error", it) }
        }
    }
}

class PluginDiscoveryToolHandler(
    context: Context,
    private val listPlugins: suspend () -> List<OmniPluginState>,
) : ToolHandler {
    private val helper = SharedHelper(context.applicationContext, Json)

    override val toolNames: Set<String> = TOOL_NAMES

    override suspend fun execute(
        toolCall: AssistantToolCall,
        args: JsonObject,
        runtimeDescriptor: AgentToolRegistry.RuntimeToolDescriptor,
        env: AgentExecutionEnvironment,
        callback: AgentCallback,
        toolHandle: AgentToolExecutionHandle,
    ): ToolExecutionResult {
        val payload = when (toolCall.function.name) {
            LIST_TOOL -> {
                val installedOnly = args["installed_only"]?.jsonPrimitive?.booleanOrNull == true
                PluginDiscoveryPayloads.list(listPlugins(), installedOnly)
            }
            GET_TOOL -> {
                val pluginId = args["plugin_id"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return ToolExecutionResult.Error(GET_TOOL, "plugin_id is required")
                PluginDiscoveryPayloads.get(listPlugins(), pluginId)
                    ?: return ToolExecutionResult.Error(GET_TOOL, "Unknown plugin: $pluginId")
            }
            else -> return ToolExecutionResult.Error(
                toolCall.function.name,
                "Unsupported plugin discovery tool",
            )
        }
        val encoded = payload.toString()
        return ToolExecutionResult.ContextResult(
            toolName = toolCall.function.name,
            summaryText = if (toolCall.function.name == LIST_TOOL) {
                "已读取插件目录"
            } else {
                "已读取插件详情"
            },
            previewJson = encoded,
            rawResultJson = encoded,
        )
    }

    companion object {
        const val LIST_TOOL = "plugin_list"
        const val GET_TOOL = "plugin_get"
        val TOOL_NAMES = setOf(LIST_TOOL, GET_TOOL)

        fun definitions(): List<OmniPluginToolDefinition> = listOf(
            OmniPluginToolDefinition(
                name = LIST_TOOL,
                displayName = "Plugin List",
                description =
                    "List official plugins and their installed/enabled state. This is read-only.",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("installed_only", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Return only installed plugins.")
                        })
                    })
                    put("additionalProperties", false)
                },
            ),
            OmniPluginToolDefinition(
                name = GET_TOOL,
                displayName = "Plugin Details",
                description =
                    "Read one plugin's capabilities and status. Installation still requires the user.",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("required", buildJsonArray { add(JsonPrimitive("plugin_id")) })
                    put("properties", buildJsonObject {
                        put("plugin_id", buildJsonObject {
                            put("type", "string")
                            put("description", "Plugin id returned by plugin_list.")
                        })
                    })
                    put("additionalProperties", false)
                },
            ),
        )
    }
}
