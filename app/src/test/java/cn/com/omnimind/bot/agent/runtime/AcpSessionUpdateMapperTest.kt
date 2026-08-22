@file:OptIn(com.agentclientprotocol.annotations.UnstableApi::class)

package cn.com.omnimind.bot.agent.runtime

import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.MessageId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapper is the single ACP -> UI translation shared by the local runtime and
 * (once it forwards ACP) the remote PC Bridge, so its output shape is a
 * contract worth pinning down.
 */
class AcpSessionUpdateMapperTest {

    @Test
    fun agentMessageChunkKeepsItsMessageIdAsTheItemId() {
        val event = SessionUpdate.AgentMessageChunk(
            content = ContentBlock.Text("hello"),
            messageId = MessageId("msg_a")
        ).toAcpUiEvent("thread-1")

        assertEquals("item/agentMessage/delta", event?.method)
        assertEquals("msg_a", event?.params?.get("itemId"))
        assertEquals("hello", event?.params?.get("delta"))
    }

    @Test
    fun agentMessageChunkWithoutMessageIdFallsBackToTheTurn() {
        val event = SessionUpdate.AgentMessageChunk(
            content = ContentBlock.Text("hello")
        ).toAcpUiEvent("thread-1", "turn-1")

        assertEquals("turn-1-agent", event?.params?.get("itemId"))
    }

    @Test
    fun agentMessageChunksWithoutIdsDoNotCollideAcrossTurns() {
        fun itemId(turnId: String): Any? = SessionUpdate.AgentMessageChunk(
            content = ContentBlock.Text("hello")
        ).toAcpUiEvent("thread-1", turnId)?.params?.get("itemId")

        assertEquals("turn-1-agent", itemId("turn-1"))
        assertEquals("turn-2-agent", itemId("turn-2"))
    }

    @Test
    fun agentThoughtChunkUsesTheReasoningDeltaContract() {
        val event = SessionUpdate.AgentThoughtChunk(
            content = ContentBlock.Text("先检查消息顺序"),
            messageId = MessageId("msg_thinking")
        ).toAcpUiEvent("thread-1")

        assertEquals("item/reasoning/delta", event?.method)
        assertEquals("msg_thinking", event?.params?.get("itemId"))
        assertEquals("先检查消息顺序", event?.params?.get("delta"))
    }

    @Test
    fun toolCallUpdateOnlyCompletesOnATerminalStatus() {
        fun methodFor(status: ToolCallStatus?): String? = SessionUpdate.ToolCallUpdate(
            toolCallId = ToolCallId("call-1"),
            status = status
        ).toAcpUiEvent("thread-1")?.method

        assertEquals("item/completed", methodFor(ToolCallStatus.COMPLETED))
        assertEquals("item/completed", methodFor(ToolCallStatus.FAILED))
        assertEquals("item/updated", methodFor(ToolCallStatus.IN_PROGRESS))
        assertEquals("item/updated", methodFor(ToolCallStatus.PENDING))
        assertEquals("item/updated", methodFor(null))
    }

    @Test
    fun userMessageChunkProducesNothing() {
        // The client authored the user's message; replaying it back adds nothing
        // to the timeline.
        assertNull(
            SessionUpdate.UserMessageChunk(content = ContentBlock.Text("hi"))
                .toAcpUiEvent("thread-1")
        )
    }

    @Test
    fun sessionInfoUpdateWithoutATitleProducesNothing() {
        assertNull(SessionUpdate.SessionInfoUpdate(title = null).toAcpUiEvent("thread-1"))
        assertNull(SessionUpdate.SessionInfoUpdate(title = "  ").toAcpUiEvent("thread-1"))

        val renamed = SessionUpdate.SessionInfoUpdate(title = "Renamed")
            .toAcpUiEvent("thread-1")
        assertEquals("thread/name/updated", renamed?.method)
        assertEquals("Renamed", renamed?.params?.get("name"))
    }

    @Test
    fun onlyTimelineUpdatesAreTurnScoped() {
        // A turn-scoped update with no resolvable turn is dropped rather than
        // rendered as its own pseudo turn; session-scoped ones still go through
        // between turns. Getting this wrong is what produced one agent avatar
        // and one "processing" row per streamed item.
        assertTrue(
            SessionUpdate.AgentMessageChunk(content = ContentBlock.Text("x")).isTurnScoped()
        )
        assertTrue(
            SessionUpdate.AgentThoughtChunk(content = ContentBlock.Text("x")).isTurnScoped()
        )
        assertTrue(
            SessionUpdate.ToolCall(toolCallId = ToolCallId("c"), title = "t").isTurnScoped()
        )
        assertTrue(
            SessionUpdate.ToolCallUpdate(toolCallId = ToolCallId("c")).isTurnScoped()
        )

        assertFalse(SessionUpdate.SessionInfoUpdate(title = "t").isTurnScoped())
        assertFalse(
            SessionUpdate.AvailableCommandsUpdate(availableCommands = emptyList())
                .isTurnScoped()
        )
    }

    @Test
    fun toolKindsMapOntoTheUiItemTypes() {
        assertEquals("commandExecution", acpToolItemType("EXECUTE"))
        assertEquals("fileChange", acpToolItemType("EDIT"))
        assertEquals("fileChange", acpToolItemType("DELETE"))
        assertEquals("fileChange", acpToolItemType("MOVE"))
        assertEquals("webSearch", acpToolItemType("SEARCH"))
        assertEquals("webSearch", acpToolItemType("FETCH"))
        assertEquals("plan", acpToolItemType("THINK"))
        assertEquals("tool", acpToolItemType("OTHER"))
        assertEquals("tool", acpToolItemType(null))
    }
}
