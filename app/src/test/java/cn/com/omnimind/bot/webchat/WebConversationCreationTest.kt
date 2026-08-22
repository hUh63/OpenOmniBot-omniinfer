package cn.com.omnimind.bot.webchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebConversationCreationTest {
    @Test
    fun `stored conversation mode wins over a stale Agent request fallback`() {
        assertEquals(
            "codex",
            resolveWebConversationMode(
                storedMode = "codex",
                requestedMode = "normal"
            )
        )
        assertEquals(
            "chat_only",
            resolveWebConversationMode(
                storedMode = "chat_only",
                requestedMode = null
            )
        )
    }

    @Test
    fun `each stored mode selects its own runtime`() {
        assertEquals(
            WebConversationRunKind.OMNIAI,
            resolveWebConversationRunKind("normal")
        )
        assertEquals(
            WebConversationRunKind.AGENT,
            resolveWebConversationRunKind("codex")
        )
        assertEquals(
            WebConversationRunKind.CHAT_ONLY,
            resolveWebConversationRunKind("chat_only")
        )
    }

    @Test
    fun `pure chat content keeps history and current image input`() {
        val content = buildWebPureChatContent(
            existingMessages = listOf(
                mapOf(
                    "id" to "assistant-1",
                    "type" to 1,
                    "user" to 2,
                    "content" to mapOf("text" to "上一条回复")
                ),
                mapOf(
                    "id" to "user-1",
                    "type" to 1,
                    "user" to 1,
                    "content" to mapOf("text" to "上一条问题")
                )
            ),
            userMessage = "继续",
            attachments = listOf(
                mapOf(
                    "fileName" to "photo.png",
                    "mimeType" to "image/png",
                    "isImage" to true,
                    "dataUrl" to "data:image/png;base64,AA=="
                )
            )
        )

        assertEquals(listOf("user", "assistant", "user"), content.map { it["role"] })
        val currentBlocks = content.last()["content"] as List<*>
        assertEquals("text", (currentBlocks[0] as Map<*, *>)["type"])
        assertEquals("image_url", (currentBlocks[1] as Map<*, *>)["type"])
    }

    @Test
    fun `agent stream events are mapped to web updates`() {
        val assistantUpdate = parseWebAgentEvent(
            mapOf(
                "method" to "item/agentMessage/delta",
                "turnId" to "turn-1",
                "params" to mapOf(
                    "itemId" to "item-1",
                    "delta" to "hello"
                )
            )
        )
        assertEquals("hello", assistantUpdate.assistantDelta)
        assertEquals("item-1-agent-message", assistantUpdate.assistantEntryId)
        assertEquals("turn-1", assistantUpdate.parentTaskId)

        assertEquals(
            "thinking",
            parseWebAgentEvent(
                mapOf(
                    "method" to "item/reasoning/delta",
                    "turnId" to "turn-1",
                    "params" to mapOf(
                        "itemId" to "reasoning-1",
                        "delta" to "thinking"
                    )
                )
            ).reasoningDelta
        )
        assertEquals(
            "completed",
            parseWebAgentEvent(
                mapOf("method" to "turn/completed")
            ).terminalKind
        )
    }

    @Test
    fun `web agent runs explicitly request full access`() {
        val arguments = buildWebAgentTurnArguments(
            conversationId = 42L,
            userMessage = "检查权限",
            attachments = emptyList(),
            cwd = " /workspace ",
            agentId = " claude-code-acp "
        )

        assertEquals(42L, arguments["conversationId"])
        assertEquals("检查权限", arguments["text"])
        assertEquals("never", arguments["approvalPolicy"])
        assertEquals("user", arguments["approvalsReviewer"])
        assertEquals(
            mapOf("type" to "dangerFullAccess"),
            arguments["sandboxPolicy"]
        )
        assertEquals("/workspace", arguments["cwd"])
        assertEquals("claude-code-acp", arguments["agentId"])
    }

    @Test
    fun `web agent selection prefers the stored conversation binding`() {
        assertEquals(
            "opencode-acp",
            resolveWebAgentId(
                storedAgentId = "opencode-acp",
                requestedAgentId = null
            )
        )
        assertEquals(
            "claude-code-acp",
            resolveWebAgentId(
                storedAgentId = null,
                requestedAgentId = " claude-code-acp "
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `web agent selection rejects a conflicting request`() {
        resolveWebAgentId(
            storedAgentId = "codex-acp",
            requestedAgentId = "opencode-acp"
        )
    }

    @Test
    fun `agent tool lifecycle keeps a stable card id and terminal status`() {
        val started = parseWebAgentEvent(
            mapOf(
                "method" to "item/started",
                "turnId" to "turn-2",
                "params" to mapOf(
                    "item" to mapOf(
                        "id" to "command-1",
                        "type" to "commandExecution",
                        "command" to "pwd",
                        "status" to "running"
                    )
                )
            )
        ).tool
        val completed = parseWebAgentEvent(
            mapOf(
                "method" to "item/completed",
                "turnId" to "turn-2",
                "params" to mapOf(
                    "item" to mapOf(
                        "id" to "command-1",
                        "type" to "commandExecution",
                        "command" to "pwd",
                        "status" to "completed"
                    )
                )
            )
        ).tool

        assertEquals("command-1-agent-command", started?.entryId)
        assertEquals("running", started?.status)
        assertEquals(started?.entryId, completed?.entryId)
        assertEquals("success", completed?.status)
        assertEquals("turn-2", completed?.parentTaskId)
    }

    @Test
    fun `first user message becomes the conversation title like Flutter`() {
        assertEquals("帮我分析这个项目", deriveWebConversationTitle("  帮我分析这个项目  "))
        assertEquals(
            "12345678901234567890...",
            deriveWebConversationTitle("123456789012345678901234")
        )
        assertNull(deriveWebConversationTitle("   "))
    }
}
