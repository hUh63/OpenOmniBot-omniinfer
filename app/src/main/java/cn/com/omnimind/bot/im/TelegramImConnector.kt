package cn.com.omnimind.bot.im

import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal class TelegramImConnector : ImConnector {
    override val channel: ImChannelType = ImChannelType.TELEGRAM

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var status = ImConnectorStatus(channel = channel)

    @Volatile
    private var config = TelegramImConfig()

    private var pollingJob: Job? = null

    override fun currentStatus(): ImConnectorStatus = status

    override suspend fun start(
        settings: ImChannelSettings,
        onMessage: suspend (ImInboundMessage) -> Unit
    ) {
        stop()
        config = settings.telegram.normalized()
        if (!config.enabled) {
            status = ImConnectorStatus(channel = channel, enabled = false)
            return
        }
        if (config.botToken.isBlank()) {
            status = ImConnectorStatus(
                channel = channel,
                enabled = true,
                lastError = "Bot Token 未配置"
            )
            return
        }
        status = ImConnectorStatus(channel = channel, enabled = true, running = true)
        pollingJob = scope.launch {
            runPolling(config, onMessage)
        }
    }

    override suspend fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        status = status.copy(running = false, connected = false, updatedAt = System.currentTimeMillis())
    }

    override suspend fun sendText(peerId: String, text: String) {
        val activeConfig = config.normalized()
        if (activeConfig.botToken.isBlank()) {
            throw IllegalStateException("Telegram Bot Token 未配置")
        }
        val payload = JSONObject()
            .put("chat_id", normalizeChatId(peerId))
            .put("text", text)
        postTelegram(activeConfig, "sendMessage", payload, readTimeoutMs = 30_000)
    }

    override suspend fun sendTyping(peerId: String) {
        val activeConfig = config.normalized()
        if (activeConfig.botToken.isBlank()) return
        val payload = JSONObject()
            .put("chat_id", normalizeChatId(peerId))
            .put("action", "typing")
        postTelegram(activeConfig, "sendChatAction", payload, readTimeoutMs = 10_000)
    }

    private suspend fun runPolling(
        activeConfig: TelegramImConfig,
        onMessage: suspend (ImInboundMessage) -> Unit
    ) {
        var offset: Long? = null
        runCatching {
            postTelegram(
                activeConfig,
                "deleteWebhook",
                JSONObject().put("drop_pending_updates", activeConfig.dropPendingUpdates),
                readTimeoutMs = 15_000
            )
        }.onFailure { error ->
            OmniLog.w(TAG, "deleteWebhook failed: ${error.message}")
        }

        runCatching {
            val me = postTelegram(activeConfig, "getMe", JSONObject(), readTimeoutMs = 15_000)
            val user = me.optJSONObject("result")
            val username = user?.optString("username")?.takeIf { it.isNotBlank() }.orEmpty()
            status = ImConnectorStatus(
                channel = channel,
                enabled = true,
                running = true,
                connected = true,
                accountLabel = username.ifBlank { "Telegram Bot" }
            )
            registerBotCommands(activeConfig)
        }.onFailure { error ->
            status = ImConnectorStatus(
                channel = channel,
                enabled = true,
                running = true,
                connected = false,
                lastError = error.message ?: error.javaClass.simpleName
            )
        }

        while (scope.coroutineContext.isActive && pollingJob?.isActive == true) {
            try {
                val payload = JSONObject()
                    .put("timeout", 50)
                    .put("allowed_updates", JSONArray().put("message"))
                offset?.let { payload.put("offset", it) }
                val response = postTelegram(
                    activeConfig,
                    "getUpdates",
                    payload,
                    readTimeoutMs = 65_000
                )
                val updates = response.optJSONArray("result") ?: JSONArray()
                for (index in 0 until updates.length()) {
                    val update = updates.optJSONObject(index) ?: continue
                    val updateId = update.optLong("update_id", -1L)
                    if (updateId >= 0L) {
                        offset = maxOf(offset ?: 0L, updateId + 1L)
                    }
                    val message = update.optJSONObject("message") ?: continue
                    val inbound = parseInbound(message, activeConfig) ?: continue
                    onMessage(inbound)
                }
                status = status.copy(
                    enabled = true,
                    running = true,
                    connected = true,
                    lastError = "",
                    updatedAt = System.currentTimeMillis()
                )
            } catch (error: Throwable) {
                if (pollingJob?.isActive != true) return
                status = status.copy(
                    enabled = true,
                    running = true,
                    connected = false,
                    lastError = error.message ?: error.javaClass.simpleName,
                    updatedAt = System.currentTimeMillis()
                )
                OmniLog.e(TAG, "polling error: ${error.message}")
                delay(5_000)
            }
        }
    }

    private fun registerBotCommands(activeConfig: TelegramImConfig) {
        runCatching {
            postTelegram(
                activeConfig,
                "setMyCommands",
                buildCommandsPayload(
                    listOf(
                        "new" to "Start a new conversation",
                        "status" to "Show channel and session status",
                        "cancel" to "Cancel the running task",
                        "reset" to "Clear the current IM session",
                        "whoami" to "Show your chat identity",
                        "help" to "Show available commands"
                    )
                ),
                readTimeoutMs = 15_000
            )
            postTelegram(
                activeConfig,
                "setMyCommands",
                buildCommandsPayload(
                    listOf(
                        "new" to "开启新对话",
                        "status" to "查看连接和会话状态",
                        "cancel" to "取消当前任务",
                        "reset" to "清除当前 IM 会话",
                        "whoami" to "查看当前聊天身份",
                        "help" to "查看可用指令"
                    ),
                    languageCode = "zh"
                ),
                readTimeoutMs = 15_000
            )
        }.onFailure { error ->
            OmniLog.w(TAG, "setMyCommands failed: ${error.message}")
        }
    }

    private fun buildCommandsPayload(
        commands: List<Pair<String, String>>,
        languageCode: String? = null
    ): JSONObject {
        val payload = JSONObject()
            .put(
                "commands",
                JSONArray().also { array ->
                    commands.forEach { (command, description) ->
                        array.put(
                            JSONObject()
                                .put("command", command)
                                .put("description", description)
                        )
                    }
                }
            )
        if (!languageCode.isNullOrBlank()) {
            payload.put("language_code", languageCode)
        }
        return payload
    }

    private fun parseInbound(
        message: JSONObject,
        activeConfig: TelegramImConfig
    ): ImInboundMessage? {
        val chat = message.optJSONObject("chat") ?: return null
        val chatId = chat.optLong("id", Long.MIN_VALUE)
        if (chatId == Long.MIN_VALUE) return null
        val peerId = chatId.toString()
        val username = chat.optString("username").takeIf { it.isNotBlank() }
        val allowed = activeConfig.allowedChatIds
        if (allowed.isNotEmpty() &&
            peerId !in allowed &&
            username?.let { "@$it" in allowed || it in allowed } != true
        ) {
            return null
        }
        val text = sequenceOf(
            message.optString("text"),
            message.optString("caption")
        ).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (text.isEmpty()) return null
        val first = chat.optString("first_name").trim()
        val last = chat.optString("last_name").trim()
        val title = chat.optString("title").trim()
        val display = sequenceOf(
            username?.let { "@$it" },
            listOf(first, last).filter { it.isNotBlank() }.joinToString(" "),
            title
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        return ImInboundMessage(
            channel = channel,
            peerId = peerId,
            peerDisplayName = display,
            text = text,
            messageId = message.optLong("message_id", 0L).takeIf { it > 0L }?.toString().orEmpty(),
            timestamp = message.optLong("date", 0L).takeIf { it > 0L }?.let { it * 1000L }
                ?: System.currentTimeMillis()
        )
    }

    private fun postTelegram(
        activeConfig: TelegramImConfig,
        method: String,
        payload: JSONObject,
        readTimeoutMs: Int
    ): JSONObject {
        val base = activeConfig.apiBaseUrl.trimEnd('/')
        val url = URL("$base/bot${activeConfig.botToken}/$method")
        val body = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body) }
            val responseText = (if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val response = JSONObject(responseText.ifBlank { "{}" })
            if (connection.responseCode !in 200..299 || !response.optBoolean("ok", false)) {
                val description = response.optString("description")
                    .takeIf { it.isNotBlank() }
                    ?: "Telegram API ${connection.responseCode}"
                throw IllegalStateException(description)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeChatId(peerId: String): Any {
        return peerId.toLongOrNull() ?: peerId
    }

    companion object {
        private const val TAG = "[TelegramImConnector]"
    }
}
