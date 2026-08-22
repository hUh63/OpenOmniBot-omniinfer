package cn.com.omnimind.bot.plugin.official

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OmniLinkAgentProviderTest {
    @Test
    fun incomingMessagesAreDeliveredOnlyToTheirRecipientAgent() {
        val message = rawAgentMessage("omnibot-omnilink-agent")
        assertNotNull(toIncomingEvent(message, expectedRecipientAgentId = "omnibot-omnilink-agent"))
        assertNull(toIncomingEvent(message, expectedRecipientAgentId = "another-agent"))
    }

    @Test
    fun explicitReadsCanStillInspectAWellFormedMessageWithoutAFilter() {
        assertNotNull(toIncomingEvent(rawAgentMessage("another-agent")))
    }

    private fun rawAgentMessage(recipientAgentId: String): Map<String, Any?> = mapOf(
        "id" to "event-1",
        "deviceid" to "device-b",
        "data" to mapOf(
            "type" to "AGENT_MESSAGE_RECEIVED",
            "sourceDeviceId" to "device-a",
            "payload" to mapOf(
                "messageId" to "message-1",
                "conversationId" to "conversation-1",
                "message" to "请回报状态",
                "senderAgentId" to "omnibot-omnilink-agent",
                "recipientAgentId" to recipientAgentId,
            ),
        ),
    )
}
