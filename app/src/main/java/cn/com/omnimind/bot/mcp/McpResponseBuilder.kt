package cn.com.omnimind.bot.mcp

object McpResponseBuilder {
    fun buildTextResponse(text: String): Map<String, Any?> = mapOf(
        "content" to listOf(mapOf("type" to "text", "text" to text))
    )

    fun buildErrorText(message: String): Map<String, Any?> = mapOf(
        "content" to listOf(mapOf("type" to "text", "text" to message)),
        "isError" to true
    )
}
