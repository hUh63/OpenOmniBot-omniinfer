package cn.com.omnimind.bot.mcp

import cn.com.omnimind.baselib.i18n.AppLocaleManager

/**
 * MCP 工具定义
 */
object McpToolDefinitions {
    private fun brandName(): String = AppLocaleManager.brandName()

    val fileTransferTool
        get() = mapOf(
        "name" to "file_transfer",
        "description" to """Retrieve files shared to the ${brandName()} app on the Android device.

Ask the user to open or share a file with ${brandName()}, then call this tool to
fetch file metadata and a short-lived download URL.

ACTIONS:
- latest (default): return the most recently received file
- wait: block until a new file arrives (timeoutMs, default 120000)
- list: list recent received files
- get: fetch a file by fileId
- clear: delete one file (fileId) or all files

NOTES:
- Files are stored temporarily on the device (about 2 hours).
- Download URLs are only reachable on the same LAN.
""".trimIndent(),
        "inputSchema" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "description" to "latest | wait | list | get | clear. Default: latest."
                ),
                "fileId" to mapOf(
                    "type" to "string",
                    "description" to "Target file ID (required for action=get; optional for action=clear)."
                ),
                "afterFileId" to mapOf(
                    "type" to "string",
                    "description" to "For action=wait, only return a file newer than this ID."
                ),
                "timeoutMs" to mapOf(
                    "type" to "integer",
                    "description" to "For action=wait, max wait time in milliseconds (default 120000)."
                ),
                "limit" to mapOf(
                    "type" to "integer",
                    "description" to "For action=list, max number of items to return."
                )
            )
        )
    )

    val allTools
        get() = listOf(fileTransferTool)
}
