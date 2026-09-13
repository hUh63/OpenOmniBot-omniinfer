package cn.com.omnimind.bot.im

import android.content.Context
import cn.com.omnimind.bot.webchat.AgentRunService
import cn.com.omnimind.bot.webchat.ConversationDomainService
import java.util.UUID

internal class ImCommandProcessor(
    context: Context,
    private val store: ImChannelStore,
    private val statusTextProvider: (ImInboundMessage, ImPeerSession?) -> String,
    private val pendingRunNotifier: (PendingImRun) -> Unit = {}
) {
    private val conversationService = ConversationDomainService(context.applicationContext)
    private val agentRunService = AgentRunService(context.applicationContext)

    suspend fun handle(inbound: ImInboundMessage): ImProcessorResult {
        val text = inbound.text.trim()
        if (text.isEmpty()) return ImProcessorResult()
        val session = store.getSession(inbound.channel, inbound.peerId)
        if (text.startsWith("/")) {
            return handleCommand(inbound, session, text)
        }
        return handleUserMessage(inbound, session, text)
    }

    private suspend fun handleCommand(
        inbound: ImInboundMessage,
        session: ImPeerSession?,
        rawText: String
    ): ImProcessorResult {
        val parts = rawText.split(Regex("\\s+"), limit = 2)
        val command = parts.first()
            .removePrefix("/")
            .substringBefore('@')
            .trim()
            .lowercase()
        val argument = parts.getOrNull(1)?.trim().orEmpty()
        return when (command) {
            "help", "h" -> ImProcessorResult(listOf(helpText()))
            "status" -> ImProcessorResult(listOf(statusTextProvider(inbound, session)))
            "new", "mode" -> {
                val mode = normalizeImConversationMode(argument.ifBlank { "agent" })
                if (mode == null) {
                    ImProcessorResult(listOf("未知模式：$argument\n用法：/new [agent|chat|codex]"))
                } else {
                    val next = createSession(inbound, mode)
                    store.saveSession(next)
                    ImProcessorResult(
                        listOf("已开启 ${imModeLabel(mode)} 对话。\n直接发送消息即可继续。")
                    )
                }
            }

            "cancel", "stop" -> cancelSessionTask(session)
            "reset", "close" -> {
                store.clearSession(inbound.channel, inbound.peerId)
                ImProcessorResult(listOf("已清除当前 IM 会话，下次消息会默认开启 agent 对话。"))
            }

            "whoami", "id" -> ImProcessorResult(
                listOf(
                    "channel=${inbound.channel.id}\npeerId=${inbound.peerId}" +
                        inbound.peerDisplayName.takeIf { it.isNotBlank() }?.let { "\nname=$it" }.orEmpty()
                )
            )

            else -> ImProcessorResult(listOf("未知指令：/$command\n发送 /help 查看可用指令。"))
        }
    }

    private suspend fun handleUserMessage(
        inbound: ImInboundMessage,
        currentSession: ImPeerSession?,
        rawText: String
    ): ImProcessorResult {
        val normalized = normalizeUserText(rawText)
        val session = currentSession
            ?: createSession(inbound, "normal", normalized.text).also(store::saveSession)
        val activeTaskId = session.activeTaskId?.takeIf { it.isNotBlank() }
        if (activeTaskId != null) {
            // 当前会话已有运行中的任务：仍然把这条 IM 消息落库为用户气泡，
            // 否则聊天页上完全看不到这条用户输入（无论是 VLM 补充信息还是被拒绝的追加消息）。
            val followUpEntryId = "$activeTaskId-user-followup-${System.currentTimeMillis()}"
            try {
                conversationService.appendUserMessage(
                    conversationId = session.conversationId,
                    conversationMode = session.mode,
                    entryId = followUpEntryId,
                    text = rawText
                )
            } catch (error: Throwable) {
                return ImProcessorResult(
                    listOf("保存补充消息失败：${error.message ?: error.javaClass.simpleName}")
                )
            }
            if (session.awaitingInput) {
                return try {
                    val pendingRun = PendingImRun(
                        taskId = activeTaskId,
                        channel = inbound.channel,
                        peerId = inbound.peerId,
                        conversationId = session.conversationId,
                        mode = session.mode
                    )
                    pendingRunNotifier(pendingRun)
                    agentRunService.clarifyTask(activeTaskId, rawText)
                    store.saveSession(session.copy(awaitingInput = false))
                    ImProcessorResult(pendingRun = pendingRun)
                } catch (error: Throwable) {
                    ImProcessorResult(
                        replies = listOf(
                            "提交补充信息失败：${error.message ?: error.javaClass.simpleName}"
                        ),
                        finishedTaskId = activeTaskId
                    )
                }
            }
            return ImProcessorResult(listOf("当前会话已有任务运行中。发送 /cancel 可取消后重新提问。"))
        }

        val taskId = UUID.randomUUID().toString()
        val userMessageCreatedAt = System.currentTimeMillis()
        return try {
            if (currentSession != null) {
                applyFirstMessageTitleIfNeeded(session, inbound, normalized.text)
            }
            // 先把用户消息落库并发出 messagesChanged，再启动 agent。
            // 否则 agent 流事件可能先到达 Flutter，触发 hasInFlightTask=true，
            // 使聊天页在随后的 messagesChanged 上走 in-memory 分支，遗漏这条
            // 来自 IM 的用户消息。
            conversationService.appendUserMessage(
                conversationId = session.conversationId,
                conversationMode = session.mode,
                entryId = "$taskId-user",
                text = normalized.text,
                createdAt = userMessageCreatedAt
            )
            val pendingRun = PendingImRun(
                taskId = taskId,
                channel = inbound.channel,
                peerId = inbound.peerId,
                conversationId = session.conversationId,
                mode = session.mode
            )
            store.saveSession(
                session.copy(
                    activeTaskId = taskId,
                    awaitingInput = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
            pendingRunNotifier(pendingRun)
            agentRunService.startConversationRun(
                conversationId = session.conversationId,
                request = mapOf(
                    "taskId" to taskId,
                    "conversationMode" to session.mode,
                    "userMessage" to normalized.text,
                    "userMessageCreatedAt" to userMessageCreatedAt
                )
            )
            val replies = if (normalized.truncated) {
                listOf("消息较长，已保留前 $MAX_INBOUND_CHARS 字处理。")
            } else {
                emptyList()
            }
            ImProcessorResult(
                replies = replies,
                pendingRun = pendingRun
            )
        } catch (error: Throwable) {
            store.clearActiveTask(taskId)
            ImProcessorResult(
                replies = listOf("启动任务失败：${error.message ?: error.javaClass.simpleName}"),
                finishedTaskId = taskId
            )
        }
    }

    private suspend fun cancelSessionTask(session: ImPeerSession?): ImProcessorResult {
        val taskId = session?.activeTaskId?.takeIf { it.isNotBlank() }
            ?: return ImProcessorResult(listOf("当前 IM 会话没有运行中的任务。"))
        return try {
            agentRunService.cancelTask(taskId)
            store.clearActiveTask(taskId)
            ImProcessorResult(
                replies = listOf("已取消当前任务。"),
                finishedTaskId = taskId
            )
        } catch (error: Throwable) {
            ImProcessorResult(listOf("取消失败：${error.message ?: error.javaClass.simpleName}"))
        }
    }

    private suspend fun createSession(
        inbound: ImInboundMessage,
        mode: String,
        initialMessage: String? = null
    ): ImPeerSession {
        val title = buildConversationTitle(inbound, initialMessage)
        val payload = conversationService.createConversation(
            title = title,
            mode = mode,
            summary = "IM ${inbound.channel.title} ${inbound.peerId}"
        )
        val conversationId = readLong(payload["id"])
            ?: throw IllegalStateException("创建对话失败")
        return ImPeerSession(
            channel = inbound.channel,
            peerId = inbound.peerId,
            displayName = inbound.peerDisplayName,
            conversationId = conversationId,
            mode = mode
        )
    }

    private suspend fun applyFirstMessageTitleIfNeeded(
        session: ImPeerSession,
        inbound: ImInboundMessage,
        firstMessage: String
    ) {
        runCatching {
            val payload = conversationService.getConversationPayload(session.conversationId)
                ?: return@runCatching
            val messageCount = readLong(payload["messageCount"]) ?: 0L
            if (messageCount > 0L) return@runCatching
            val currentTitle = payload["title"]?.toString()?.trim().orEmpty()
            if (!isImPlaceholderTitle(currentTitle, inbound)) return@runCatching

            val nextTitle = buildConversationTitle(inbound, firstMessage)
            if (nextTitle != currentTitle) {
                conversationService.updateConversationTitle(session.conversationId, nextTitle)
            }
        }
    }

    private fun isImPlaceholderTitle(title: String, inbound: ImInboundMessage): Boolean {
        val prefix = "IM ${inbound.channel.title} "
        if (!title.startsWith(prefix)) return false

        val display = inbound.peerDisplayName.takeIf { it.isNotBlank() } ?: inbound.peerId
        val currentFallback = "$prefix${display.take(MAX_TITLE_MESSAGE_CHARS)}"
        if (title == currentFallback) return true

        val legacyFallbackPrefix = "$prefix${display.take(LEGACY_TITLE_PEER_CHARS)}"
        return title.startsWith(legacyFallbackPrefix) &&
            Regex("\\s\\d{2}-\\d{2}\\s\\d{2}:\\d{2}$").containsMatchIn(title)
    }

    private fun buildConversationTitle(
        inbound: ImInboundMessage,
        initialMessage: String?
    ): String {
        val messagePreview = initialMessage
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { text ->
                if (text.length > MAX_TITLE_MESSAGE_CHARS) {
                    "${text.take(MAX_TITLE_MESSAGE_CHARS)}..."
                } else {
                    text
                }
            }

        if (messagePreview != null) {
            return "IM ${inbound.channel.title} $messagePreview"
        }

        val display = inbound.peerDisplayName.takeIf { it.isNotBlank() } ?: inbound.peerId
        return "IM ${inbound.channel.title} ${display.take(MAX_TITLE_MESSAGE_CHARS)}"
    }

    private fun normalizeUserText(text: String): NormalizedImText {
        val sanitized = text.trim()
        if (sanitized.length <= MAX_INBOUND_CHARS) {
            return NormalizedImText(sanitized, truncated = false)
        }
        return NormalizedImText(sanitized.take(MAX_INBOUND_CHARS), truncated = true)
    }

    private fun helpText(): String {
        return """
            可用指令：
            /new [agent|chat|codex] 开启新对话，默认 agent
            /status 查看连接和当前会话
            /cancel 取消当前任务
            /reset 清除当前 IM 会话
            /whoami 查看当前 peerId
            /help 查看本说明
        """.trimIndent()
    }

    private fun readLong(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private data class NormalizedImText(
        val text: String,
        val truncated: Boolean
    )

    companion object {
        private const val MAX_INBOUND_CHARS = 32_000
        private const val MAX_TITLE_MESSAGE_CHARS = 20
        private const val LEGACY_TITLE_PEER_CHARS = 24
    }
}
