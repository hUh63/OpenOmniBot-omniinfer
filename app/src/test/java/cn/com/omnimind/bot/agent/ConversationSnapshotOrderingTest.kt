package cn.com.omnimind.bot.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ConversationSnapshotOrderingTest {

    @Test
    fun `prepareForStorage sorts flutter local iso timestamps from oldest to newest`() {
        val messages = listOf(
            assistantMessage(
                id = "assistant",
                createAt = "2026-03-31T18:00:03.300",
                text = "assistant"
            ),
            deepThinkingMessage(
                id = "thinking",
                createAt = "2026-03-31T18:00:02.200",
                taskId = "task-1",
                startTime = localMillis("2026-03-31T18:00:02.200"),
                thinking = "thinking"
            ),
            userMessage(
                id = "user",
                createAt = "2026-03-31T18:00:01.100",
                text = "user"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(listOf("user", "thinking", "assistant"), orderedIds)
    }

    @Test
    fun `prepareForStorage keeps newest-first snapshot tie order reversible when timestamps match`() {
        val messages = listOf(
            assistantMessage(
                id = "1711872000300-text",
                createAt = "invalid",
                text = "assistant"
            ),
            deepThinkingMessage(
                id = "1711872000300-thinking",
                createAt = "invalid",
                taskId = "1711872000300-ai",
                startTime = 1711872000300L,
                thinking = "thinking"
            ),
            userMessage(
                id = "1711872000300-user",
                createAt = "invalid",
                text = "user"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(
            listOf(
                "1711872000300-user",
                "1711872000300-thinking",
                "1711872000300-text"
            ),
            orderedIds
        )
    }

    @Test
    fun `sortForDisplay keeps deep thinking between user and assistant after retry restore`() {
        val messages = listOf(
            userMessage(
                id = "1711872001200-user",
                createAt = "2026-03-31T18:00:01.200",
                text = "第二轮用户"
            ),
            assistantMessage(
                id = "1711872001200-text",
                createAt = "2026-03-31T18:00:01.202",
                text = "第二轮助手"
            ),
            deepThinkingMessage(
                id = "1711872001200-thinking",
                createAt = "2026-03-31T18:00:01.201",
                taskId = "1711872001200-ai",
                startTime = localMillis("2026-03-31T18:00:01.201"),
                thinking = "第二轮思考"
            ),
            userMessage(
                id = "1711872000100-user",
                createAt = "2026-03-31T18:00:00.100",
                text = "第一轮用户"
            ),
            assistantMessage(
                id = "1711872000100-text",
                createAt = "2026-03-31T18:00:00.102",
                text = "第一轮助手"
            ),
            deepThinkingMessage(
                id = "1711872000100-thinking",
                createAt = "2026-03-31T18:00:00.101",
                taskId = "1711872000100-ai",
                startTime = localMillis("2026-03-31T18:00:00.101"),
                thinking = "第一轮思考"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.sortForDisplay(messages)
            .map { it["id"] }

        assertEquals(
            listOf(
                "1711872001200-text",
                "1711872001200-thinking",
                "1711872001200-user",
                "1711872000100-text",
                "1711872000100-thinking",
                "1711872000100-user"
            ),
            orderedIds
        )
    }

    @Test
    fun `prepareForStorage keeps logical phase order when persisted timestamps drift inside one task`() {
        val messages = listOf(
            assistantMessage(
                id = "1711872000100-ai-assistant",
                createAt = "2026-03-31T18:00:00.300",
                text = "助手回答"
            ),
            userMessage(
                id = "1711872000100-ai-user",
                createAt = "2026-03-31T18:00:00.200",
                text = "用户提问"
            ),
            deepThinkingMessage(
                id = "1711872000100-ai-thinking",
                createAt = "2026-03-31T18:00:00.100",
                taskId = "1711872000100-ai",
                startTime = localMillis("2026-03-31T18:00:00.100"),
                thinking = "思考过程"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(
            listOf(
                "1711872000100-ai-user",
                "1711872000100-ai-thinking",
                "1711872000100-ai-assistant"
            ),
            orderedIds
        )
    }

    @Test
    fun `prepareForStorage prefers stream meta sequence for new agent entries`() {
        val messages = listOf(
            toolMessage(
                id = "1711872000100-ai-tool-1",
                createAt = "2026-03-31T18:00:00.140",
                taskId = "1711872000100-ai",
                summary = "工具执行",
                streamSeq = 3L,
                roundIndex = 1,
                kind = "tool_completed"
            ),
            assistantMessage(
                id = "1711872000100-ai-text",
                createAt = "2026-03-31T18:00:00.150",
                text = "正文",
                streamSeq = 2L,
                roundIndex = 1,
                kind = "text_snapshot"
            ),
            deepThinkingMessage(
                id = "1711872000100-ai-thinking",
                createAt = "2026-03-31T18:00:00.160",
                taskId = "1711872000100-ai",
                startTime = localMillis("2026-03-31T18:00:00.100"),
                thinking = "思考",
                streamSeq = 1L,
                roundIndex = 1,
                kind = "thinking_snapshot"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(
            listOf(
                "1711872000100-ai-thinking",
                "1711872000100-ai-text",
                "1711872000100-ai-tool-1"
            ),
            orderedIds
        )
    }

    @Test
    fun `prepareForStorage prefers stable entry sequence when stream update sequence drifts`() {
        val messages = listOf(
            assistantMessage(
                id = "1783062175067-ai-text-2",
                createAt = "2026-07-03T15:03:38.564",
                text = "任务已被手动停止。需要换一种方式发送吗？",
                streamSeq = 105L,
                entrySeq = 5L,
                roundIndex = 2,
                kind = "text_snapshot"
            ),
            deepThinkingMessage(
                id = "1783062175067-ai-thinking-2",
                createAt = "2026-07-03T15:03:34.039",
                taskId = "1783062175067-ai",
                startTime = localMillis("2026-07-03T15:03:34.039"),
                thinking = "第二段思考",
                streamSeq = 104L,
                entrySeq = 4L,
                roundIndex = 2,
                kind = "thinking_snapshot"
            ),
            toolMessage(
                id = "1783062175067-ai-tool-1",
                createAt = "2026-07-03T15:03:14.995",
                taskId = "1783062175067-ai",
                summary = "发送早安短信",
                streamSeq = 69L,
                entrySeq = 3L,
                roundIndex = 1,
                kind = "tool_completed"
            ),
            assistantMessage(
                id = "1783062175067-ai-text",
                createAt = "2026-07-03T15:03:13.651",
                text = "好的，我来通过手机屏幕自动化发送这条短信。",
                streamSeq = 61L,
                entrySeq = 2L,
                roundIndex = 1,
                kind = "text_snapshot"
            ),
            deepThinkingMessage(
                id = "1783062175067-ai-thinking",
                createAt = "2026-07-03T15:02:57.211",
                taskId = "1783062175067-ai",
                startTime = localMillis("2026-07-03T15:02:57.211"),
                thinking = "第一段思考",
                streamSeq = 70L,
                entrySeq = 1L,
                roundIndex = 1,
                kind = "thinking_snapshot"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(
            listOf(
                "1783062175067-ai-thinking",
                "1783062175067-ai-text",
                "1783062175067-ai-tool-1",
                "1783062175067-ai-thinking-2",
                "1783062175067-ai-text-2"
            ),
            orderedIds
        )
    }

    @Test
    fun `prepareForStorage uses creation order when a Xiaowan run is partially sequenced`() {
        val taskId = "1786765957366-ai"
        val messages = listOf(
            assistantMessage(
                id = "$taskId-text-7",
                createAt = "2026-08-15T11:55:14.292",
                text = "诊断完成"
            ),
            assistantMessage(
                id = "$taskId-text-8",
                createAt = "2026-08-15T11:55:18.611",
                text = "最终环境诊断结果",
                streamSeq = 839L,
                entrySeq = 25L,
                roundIndex = 8,
                kind = "text_snapshot"
            ),
            deepThinkingMessage(
                id = "$taskId-thinking-9",
                createAt = "2026-08-15T11:55:16.801",
                taskId = taskId,
                startTime = localMillis("2026-08-15T11:55:16.801"),
                thinking = "最终整理",
                streamSeq = 444L,
                entrySeq = 24L,
                roundIndex = 9,
                kind = "thinking_snapshot"
            ),
            toolMessage(
                id = "$taskId-tool-8",
                createAt = "2026-08-15T11:55:16.580",
                taskId = taskId,
                summary = "写入记忆",
                streamSeq = 441L,
                entrySeq = 23L,
                roundIndex = 8,
                kind = "tool_completed"
            ),
            deepThinkingMessage(
                id = "$taskId-thinking-8",
                createAt = "2026-08-15T11:55:07.772",
                taskId = taskId,
                startTime = localMillis("2026-08-15T11:55:07.772"),
                thinking = "检查 SSH",
                streamSeq = 422L,
                entrySeq = 21L,
                roundIndex = 8,
                kind = "thinking_snapshot"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(
            listOf(
                "$taskId-thinking-8",
                "$taskId-text-7",
                "$taskId-tool-8",
                "$taskId-thinking-9",
                "$taskId-text-8"
            ),
            orderedIds
        )
    }

    @Test
    fun `prepareForStorage keeps continued agent run after previous reset stream sequence`() {
        val messages = listOf(
            deepThinkingMessage(
                id = "1711872000100-ai-thinking",
                createAt = "2026-03-31T18:00:00.100",
                taskId = "1711872000100-ai",
                startTime = localMillis("2026-03-31T18:00:00.100"),
                thinking = "中断前思考",
                streamSeq = 1L,
                entrySeq = 1L,
                roundIndex = 1,
                kind = "thinking_snapshot"
            ),
            toolMessage(
                id = "1711872000100-ai-tool-1",
                createAt = "2026-03-31T18:00:00.200",
                taskId = "1711872000100-ai",
                summary = "中断前工具",
                streamSeq = 2L,
                roundIndex = 1,
                kind = "tool_completed"
            ),
            assistantMessage(
                id = "1711872000100-ai-text",
                createAt = "2026-03-31T18:00:00.300",
                text = "Agent execution failed",
                streamSeq = 6L,
                entrySeq = 6L,
                roundIndex = 1,
                kind = "text_snapshot"
            ),
            deepThinkingMessage(
                id = "1711872000100-ai-thinking-c1",
                createAt = "2026-03-31T18:00:01.100",
                taskId = "1711872000100-ai",
                startTime = localMillis("2026-03-31T18:00:01.100"),
                thinking = "继续后的思考",
                streamSeq = 1L,
                entrySeq = 1L,
                roundIndex = 1,
                kind = "thinking_snapshot"
            ),
            assistantMessage(
                id = "1711872000100-ai-text-2",
                createAt = "2026-03-31T18:00:01.200",
                text = "继续后的正文",
                streamSeq = 2L,
                entrySeq = 2L,
                roundIndex = 2,
                kind = "text_snapshot"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(
            listOf(
                "1711872000100-ai-thinking",
                "1711872000100-ai-tool-1",
                "1711872000100-ai-text",
                "1711872000100-ai-thinking-c1",
                "1711872000100-ai-text-2"
            ),
            orderedIds
        )
    }

    @Test
    fun `sortForDisplay preserves interleaved thinking rounds by timestamp within one reply`() {
        val messages = listOf(
            deepThinkingMessage(
                id = "1711872000100-ai-thinking-2",
                createAt = "2026-03-31T18:00:00.130",
                taskId = "1711872000100-ai",
                startTime = localMillis("2026-03-31T18:00:00.130"),
                thinking = "第二段思考"
            ),
            assistantMessage(
                id = "1711872000100-ai-assistant",
                createAt = "2026-03-31T18:00:00.120",
                text = "开始输出回答"
            ),
            deepThinkingMessage(
                id = "1711872000100-ai-thinking",
                createAt = "2026-03-31T18:00:00.110",
                taskId = "1711872000100-ai",
                startTime = localMillis("2026-03-31T18:00:00.110"),
                thinking = "第一段思考"
            ),
            userMessage(
                id = "1711872000100-ai-user",
                createAt = "2026-03-31T18:00:00.100",
                text = "用户提问"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.sortForDisplay(messages)
            .map { it["id"] }

        assertEquals(
            listOf(
                "1711872000100-ai-thinking-2",
                "1711872000100-ai-assistant",
                "1711872000100-ai-thinking",
                "1711872000100-ai-user"
            ),
            orderedIds
        )
    }

    @Test
    fun `prepareForStorage does not treat uuid prefixes as timestamps`() {
        val messages = listOf(
            assistantMessage(
                id = "1fedcba0-0000-0000-0000-000000000000-agent-message",
                createAt = "2026-07-26T08:52:03.000",
                text = "第二轮助手"
            ),
            userMessage(
                id = "1aaaaaa0-0000-0000-0000-000000000000-user",
                createAt = "2026-07-26T08:52:02.000",
                text = "第二轮用户"
            ),
            assistantMessage(
                id = "9fedcba0-0000-0000-0000-000000000000-agent-message",
                createAt = "2026-07-26T08:52:01.000",
                text = "第一轮助手"
            ),
            userMessage(
                id = "9aaaaaa0-0000-0000-0000-000000000000-user",
                createAt = "2026-07-26T08:52:00.000",
                text = "第一轮用户"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(
            listOf(
                "9aaaaaa0-0000-0000-0000-000000000000-user",
                "9fedcba0-0000-0000-0000-000000000000-agent-message",
                "1aaaaaa0-0000-0000-0000-000000000000-user",
                "1fedcba0-0000-0000-0000-000000000000-agent-message"
            ),
            orderedIds
        )
    }

    @Test
    fun `prepareForStorage treats numeric leading Claude message ids as opaque`() {
        val messages = listOf(
            assistantMessage(
                id = "021785489612755572fdc12977fa128088f145c73a50ff1fb0af3-agent-message",
                createAt = "2026-07-31T17:20:14.558+08:00",
                text = "第二轮助手"
            ),
            userMessage(
                id = "1785489611128-user",
                createAt = "2026-07-31T17:20:11.128+08:00",
                text = "第二轮用户"
            ),
            assistantMessage(
                id = "0217854895877502d60f863e4315e8806c7747df8d2270827ca82-agent-message",
                createAt = "2026-07-31T17:19:54.407+08:00",
                text = "第一轮助手"
            ),
            userMessage(
                id = "1785489582671-user",
                createAt = "2026-07-31T17:19:42.671+08:00",
                text = "第一轮用户"
            )
        )

        val orderedIds = ConversationSnapshotOrdering.prepareForStorage(messages)
            .map { it.payload["id"] }

        assertEquals(
            listOf(
                "1785489582671-user",
                "0217854895877502d60f863e4315e8806c7747df8d2270827ca82-agent-message",
                "1785489611128-user",
                "021785489612755572fdc12977fa128088f145c73a50ff1fb0af3-agent-message"
            ),
            orderedIds
        )
    }

    private fun userMessage(
        id: String,
        createAt: String,
        text: String
    ): Map<String, Any?> {
        return linkedMapOf(
            "id" to id,
            "type" to 1,
            "user" to 1,
            "content" to linkedMapOf(
                "id" to id,
                "text" to text
            ),
            "createAt" to createAt
        )
    }

    private fun assistantMessage(
        id: String,
        createAt: String,
        text: String,
        streamSeq: Long? = null,
        entrySeq: Long? = null,
        roundIndex: Int? = null,
        kind: String? = null
    ): Map<String, Any?> {
        return linkedMapOf(
            "id" to id,
            "type" to 1,
            "user" to 2,
            "content" to linkedMapOf(
                "id" to id,
                "text" to text
            ),
            "streamMeta" to streamMeta(streamSeq, entrySeq, roundIndex, kind, id),
            "createAt" to createAt
        )
    }

    private fun deepThinkingMessage(
        id: String,
        createAt: String,
        taskId: String,
        startTime: Long,
        thinking: String,
        streamSeq: Long? = null,
        entrySeq: Long? = null,
        roundIndex: Int? = null,
        kind: String? = null
    ): Map<String, Any?> {
        return linkedMapOf(
            "id" to id,
            "type" to 2,
            "user" to 3,
            "content" to linkedMapOf(
                "id" to id,
                "cardData" to linkedMapOf(
                    "type" to "deep_thinking",
                    "taskID" to taskId,
                    "thinkingContent" to thinking,
                    "startTime" to startTime,
                    "endTime" to (startTime + 1),
                    "stage" to 4,
                    "isLoading" to false
                )
            ),
            "streamMeta" to streamMeta(streamSeq, entrySeq, roundIndex, kind, taskId),
            "createAt" to createAt
        )
    }

    private fun toolMessage(
        id: String,
        createAt: String,
        taskId: String,
        summary: String,
        streamSeq: Long,
        entrySeq: Long? = null,
        roundIndex: Int,
        kind: String
    ): Map<String, Any?> {
        return linkedMapOf(
            "id" to id,
            "type" to 2,
            "user" to 3,
            "content" to linkedMapOf(
                "id" to id,
                "cardData" to linkedMapOf(
                    "type" to "agent_tool_summary",
                    "taskId" to taskId,
                    "summary" to summary
                )
            ),
            "streamMeta" to streamMeta(streamSeq, entrySeq, roundIndex, kind, taskId),
            "createAt" to createAt
        )
    }

    private fun streamMeta(
        streamSeq: Long?,
        entrySeq: Long?,
        roundIndex: Int?,
        kind: String?,
        taskId: String
    ): Map<String, Any?>? {
        if (streamSeq == null && entrySeq == null && roundIndex == null && kind == null) {
            return null
        }
        return linkedMapOf(
            "seq" to streamSeq,
            "entrySeq" to entrySeq,
            "roundIndex" to roundIndex,
            "kind" to kind,
            "parentTaskId" to taskId
        )
    }

    private fun localMillis(value: String): Long {
        return LocalDateTime.parse(value)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
