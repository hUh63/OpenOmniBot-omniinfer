package cn.com.omnimind.bot.plugin.official

import cn.com.omnimind.bot.plugin.OmniPluginToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The small, user-facing toolbox exposed by the OmniLink runtime bundle. */
object OmniLinkAgentTools {
    const val DEVICES = "omnilink_devices"
    const val CONTROL = "omnilink_control"
    const val EVENTS = "omnilink_events"

    val TOOL_NAMES = linkedSetOf(
        DEVICES,
        CONTROL,
        EVENTS,
    )

    fun definitions(): List<OmniPluginToolDefinition> = listOf(
        definition(
            name = DEVICES,
            displayName = "查看协作设备",
            description = "列出当前 OmniLink 连接中的设备、连接状态、在线可达性和 readiness。readiness.device 可用于查询电量、充电、网络、交互和锁定状态；只返回安全的设备摘要。",
        ),
        definition(
            name = CONTROL,
            displayName = "控制协作设备",
            description = "在明确设备上执行一个 typed OmniLink action。连接、状态、消息和未来能力都通过 action 与 input 表达，不为场景增加新工具。",
            required = listOf("device_id", "action"),
            properties = mapOf(
                "device_id" to stringProperty("来自 omnilink_devices 的稳定设备 ID。"),
                "action" to stringProperty("typed action，例如 connect、status 或 send_message。"),
                "input" to objectProperty("action 的结构化输入；没有输入时省略。"),
            ),
        ),
        definition(
            name = EVENTS,
            displayName = "读取或订阅协作事件",
            description = "从明确设备读取或订阅 Agent-safe 事件；mode=read 做一次读取，mode=subscribe 开启回流，mode=stop 停止回流。cursor 支持断线恢复。",
            required = listOf("device_id"),
            properties = mapOf(
                "device_id" to stringProperty("来自 omnilink_devices 的稳定设备 ID。"),
                "event_types" to arrayProperty(
                    description = "要读取的 OmniLink 事件类型，例如 AGENT_MESSAGE_RECEIVED、NOTIFICATION_UPSERTED、NOTIFICATION_REMOVED。只能使用 Agent-safe 类型。",
                ),
                "wait_ms" to integerProperty("最长等待毫秒数，范围 0 到 30000。"),
                "cursor" to stringProperty("上一次返回的 opaque cursor；不要自行修改。"),
                "mode" to enumProperty(
                    description = "read 一次读取；subscribe 开启持续回流；stop 停止回流。",
                    values = listOf("read", "subscribe", "stop"),
                ),
            ),
        ),
    )

    private fun definition(
        name: String,
        displayName: String,
        description: String,
        required: List<String> = emptyList(),
        properties: Map<String, kotlinx.serialization.json.JsonObject> = emptyMap(),
    ) = OmniPluginToolDefinition(
        name = name,
        displayName = displayName,
        description = description,
        parameters = buildJsonObject {
            put("type", "object")
            if (required.isNotEmpty()) {
                put("required", buildJsonArray {
                    required.forEach { add(JsonPrimitive(it)) }
                })
            }
            put("properties", buildJsonObject {
                properties.forEach { (key, value) -> put(key, value) }
            })
            put("additionalProperties", false)
        },
    )

    private fun stringProperty(description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun objectProperty(description: String) = buildJsonObject {
        put("type", "object")
        put("description", description)
    }

    private fun integerProperty(description: String) = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun arrayProperty(description: String) = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", buildJsonObject {
            put("type", "string")
        })
        put("minItems", 1)
        put("maxItems", 8)
    }

    private fun enumProperty(description: String, values: List<String>) = buildJsonObject {
        put("type", "string")
        put("description", description)
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }
}
