package cn.com.omnimind.bot.mcp

import android.content.Context
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Auxiliary HTTP routes that live beside the standard MCP endpoint. */
object McpRoutes {
    fun Route.registerMcpRoutes(context: Context) {
        get("/mcp/health") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/mcp/file/{fileId}") {
            McpServerManager.handleFileDownload(call)
        }

        authenticate("bearer-auth") {
            get("/mcp/state") {
                call.respond(McpServerManager.currentState().toJsonObject())
            }
        }
    }
}
