package cn.com.omnimind.bot.im

internal interface ImConnector {
    val channel: ImChannelType

    fun currentStatus(): ImConnectorStatus

    suspend fun start(
        settings: ImChannelSettings,
        onMessage: suspend (ImInboundMessage) -> Unit
    )

    suspend fun stop()

    suspend fun sendText(peerId: String, text: String)

    suspend fun sendTyping(peerId: String) {
        // Optional for connectors that support typing indicators.
    }

    suspend fun requestQr(): Map<String, Any?> {
        return mapOf(
            "ok" to false,
            "channel" to channel.id,
            "error" to "当前渠道不支持扫码绑定"
        )
    }
}
