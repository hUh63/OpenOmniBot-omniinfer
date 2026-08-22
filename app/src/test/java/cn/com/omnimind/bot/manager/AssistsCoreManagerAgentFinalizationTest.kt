package cn.com.omnimind.bot.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import cn.com.omnimind.bot.agent.AgentFinalResponse
import cn.com.omnimind.bot.agent.AgentResult

class AssistsCoreManagerAgentFinalizationTest {
    @Test
    fun `keeps streamed assistant text when agent errors after visible output`() {
        val resolution = resolveAgentFinalErrorResolution(
            streamed = "已生成正文😀",
            error = "Agent execution failed: length=140; regionStart=0; bytePairLength=138",
            localizedFallback = "暂时无法生成回复，请重试。"
        )

        assertEquals("已生成正文😀", resolution.text)
        assertFalse(resolution.persistAsError)
    }

    @Test
    fun `falls back to error details when no assistant text was streamed`() {
        val resolution = resolveAgentFinalErrorResolution(
            streamed = "",
            error = "Agent execution failed: length=140; regionStart=0; bytePairLength=138",
            localizedFallback = "暂时无法生成回复，请重试。"
        )

        assertEquals(
            "Agent execution failed: length=140; regionStart=0; bytePairLength=138",
            resolution.text
        )
        assertTrue(resolution.persistAsError)
    }

    @Test
    fun `uses localized fallback when streamed text and error details are blank`() {
        val resolution = resolveAgentFinalErrorResolution(
            streamed = "",
            error = "",
            localizedFallback = "暂时无法生成回复，请重试。"
        )

        assertEquals("暂时无法生成回复，请重试。", resolution.text)
        assertTrue(resolution.persistAsError)
    }

    @Test
    fun `manual cancellation stream metadata sorts after run trace entries`() {
        val meta = buildAgentManualCancellationStreamMeta(
            taskId = "agent-task",
            entryId = "agent-task-cancelled"
        )

        assertEquals(1_000_000_000L, meta["seq"])
        assertEquals(1_000_000_000, meta["roundIndex"])
        assertEquals("text_snapshot", meta["kind"])
        assertEquals("agent-task", meta["parentTaskId"])
        assertEquals("agent-task-cancelled", meta["entryId"])
        assertEquals(true, meta["isFinal"])
    }

    @Test
    fun `buildTurnUsageSnapshot maps per-turn usage fields onto ui payload`() {
        val success = AgentResult.Success(
            response = AgentFinalResponse(content = "done"),
            executedTools = emptyList(),
            outputKind = "text",
            hasUserVisibleOutput = true,
            latestPromptTokens = 10_000,
            promptTokenThreshold = 128_000,
            completionTokens = 87,
            cachedTokens = 10_000,
            cacheCreationTokens = 512,
            totalTokens = 10_087
        )

        val snapshot = buildTurnUsageSnapshot(
            latestPromptTokens = success.latestPromptTokens,
            promptTokenThreshold = success.promptTokenThreshold,
            result = success
        )

        assertNotNull(snapshot)
        assertEquals(10_000, snapshot?.ctxTokens)
        assertEquals(10_000, snapshot?.inputTokens)
        assertEquals(87, snapshot?.outputTokens)
        assertEquals(10_000, snapshot?.cacheTokens)
        assertEquals(10_000, snapshot?.totalInputTokens)
        assertEquals(0, snapshot?.uncachedInputTokens)
        assertEquals(10_000, snapshot?.cacheReadTokens)
        assertEquals(512, snapshot?.cacheWriteTokens)
        assertEquals(10_087, snapshot?.totalTokens)
        assertEquals(128_000, snapshot?.promptTokenThreshold)
    }
}
