package cn.com.omnimind.bot.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * MCP 服务器状态
 */
data class McpServerState(
    val enabled: Boolean,
    val running: Boolean,
    val host: String?,
    val port: Int,
    val token: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "running" to running,
        "host" to host,
        "port" to port,
        "token" to token,
    )

    fun toJsonObject(): JsonObject = JsonObject(
        mapOf(
            "enabled" to JsonPrimitive(enabled),
            "running" to JsonPrimitive(running),
            "host" to (host?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull),
            "port" to JsonPrimitive(port),
            "token" to JsonPrimitive(token),
        ),
    )
}
