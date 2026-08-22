package cn.com.omnimind.bot.agent

import cn.com.omnimind.assists.controller.http.HttpController
import cn.com.omnimind.baselib.llm.ChatCompletionMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class AgentConversationContextCompactorTest {
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `buildCompactionRequestMessages uses summary user message and replacement prompt`() {
        val requestMessages = AgentConversationContextCompactor.buildCompactionRequestMessages(
            existingSummary = "旧总结",
            messagesToCompact = listOf(
                ChatCompletionMessage(
                    role = "user",
                    content = JsonPrimitive("新问题")
                )
            )
        )

        val firstMessage = requestMessages.first()
        assertEquals("system", firstMessage["role"])
        val systemPromptContent = firstMessage["content"].toString()
        assertTrue(systemPromptContent.contains("type=text"))
        assertTrue(systemPromptContent.contains("context compaction engine"))
        assertTrue(systemPromptContent.contains("cache_control={type=ephemeral}"))
        assertTrue(systemPromptContent.contains("## Goal"))
        assertTrue(systemPromptContent.contains("## Constraints & Preferences"))
        assertTrue(systemPromptContent.contains("## Critical Context"))
        assertTrue(systemPromptContent.contains("Do NOT continue the conversation"))

        val summaryMessage = requestMessages[1]
        assertEquals("user", summaryMessage["role"])
        assertTrue(
            (summaryMessage["content"] as? String).orEmpty().startsWith(
                "<context-summary> The following is a summary of the earlier conversation that was compacted to save context space."
            )
        )
        assertTrue((summaryMessage["content"] as? String).orEmpty().contains("旧总结"))

        val compactedUserMessage = requestMessages[2]
        assertEquals("user", compactedUserMessage["role"])
        assertEquals("新问题", compactedUserMessage["content"])

        val finalPrompt = requestMessages[3]
        assertEquals("user", finalPrompt["role"])
        assertEquals(
            "Generate the replacement context summary now.",
            finalPrompt["content"]
        )
    }

    @Test
    fun `parseChatMessageContent preserves cache_control in text blocks`() {
        val method = HttpController::class.java.getDeclaredMethod(
            "parseChatMessageContent",
            Any::class.java
        )
        method.isAccessible = true

        val content = method.invoke(
            HttpController,
            listOf(
                mapOf(
                    "type" to "text",
                    "text" to "需要缓存的系统提示",
                    "cache_control" to mapOf("type" to "ephemeral")
                )
            )
        )

        val blocks = content as JsonArray
        val firstBlock = blocks.first() as JsonObject
        assertEquals("text", firstBlock["type"]?.toString()?.trim('"'))
        assertEquals(
            "ephemeral",
            firstBlock["cache_control"]
                ?.let { it as? JsonObject }
                ?.get("type")
                ?.toString()
                ?.trim('"')
        )
    }

    @Test
    fun `auto compaction trigger reserves adaptive headroom`() {
        assertEquals(
            112_000,
            AgentConversationContextCompactor.resolveAutoCompactionTrigger(128_000)
        )
        assertEquals(
            28_000,
            AgentConversationContextCompactor.resolveAutoCompactionTrigger(32_000)
        )
        assertEquals(
            5_952,
            AgentConversationContextCompactor.resolveAutoCompactionTrigger(8_000)
        )
    }

    @Test
    fun `reported context tokens include completion and prefer conservative total`() {
        assertEquals(
            12_000,
            AgentConversationContextCompactor.resolveReportedContextTokens(
                promptTokens = 10_000,
                completionTokens = 2_000,
                totalTokens = null
            )
        )
        assertEquals(
            13_000,
            AgentConversationContextCompactor.resolveReportedContextTokens(
                promptTokens = 10_000,
                completionTokens = 2_000,
                totalTokens = 13_000
            )
        )
    }

    @Test
    fun `effective context capacity never exceeds selected model limit`() {
        assertEquals(
            32_000,
            AgentConversationContextCompactor.resolveEffectiveContextCapacity(
                storedThreshold = 128_000,
                modelContextLimit = 32_000
            )
        )
        assertEquals(
            64_000,
            AgentConversationContextCompactor.resolveEffectiveContextCapacity(
                storedThreshold = 64_000,
                modelContextLimit = 128_000
            )
        )
    }
}
