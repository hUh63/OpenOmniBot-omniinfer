package cn.com.omnimind.bot.agent.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentWebLauncherTest {
    @Test
    fun webServicesUseDedicatedCommandsAndSessions() {
        assertEquals(
            AgentWebService.KIMI,
            AgentWebService.forAgentId(AcpAgentProfileStore.KIMI_CODE_AGENT_ID),
        )
        assertEquals("kimi", AgentWebService.KIMI.commandName)
        assertEquals("kimi web --no-open", AgentWebService.KIMI.command)
        assertEquals("omnibot-web-kimi", AgentWebService.KIMI.sessionId)

        assertEquals(
            AgentWebService.DEEPSEEK_HARNESS,
            AgentWebService.forAgentId(AcpAgentProfileStore.DEEPSEEK_HARNESS_AGENT_ID),
        )
        assertEquals("dsh", AgentWebService.DEEPSEEK_HARNESS.commandName)
        assertEquals("dsh web --no-open", AgentWebService.DEEPSEEK_HARNESS.command)
        assertEquals("omnibot-web-dsh", AgentWebService.DEEPSEEK_HARNESS.sessionId)
        assertNull(AgentWebService.forAgentId("codex-acp"))
    }

    @Test
    fun kimiParserKeepsOnlyLoopbackTokenUrlAndStripsAnsi() {
        val url = AgentWebUrlParser.find(
            kind = AgentWebUrlKind.KIMI,
            transcript = "\u001B[32mready\u001B[0m http://127.0.0.1:58627/#token=abc_DEF-123\n",
        )

        assertEquals("http://127.0.0.1:58627/#token=abc_DEF-123", url)
        assertNull(
            AgentWebUrlParser.find(
                kind = AgentWebUrlKind.KIMI,
                transcript = "https://example.com:58627/#token=abc",
            ),
        )
    }

    @Test
    fun deepSeekParserAcceptsOnlyLoopbackOrigin() {
        assertEquals(
            "http://localhost:3080",
            AgentWebUrlParser.find(
                kind = AgentWebUrlKind.DEEPSEEK_HARNESS,
                transcript = "dsh web: http://localhost:3080\n",
            ),
        )
        assertNull(
            AgentWebUrlParser.find(
                kind = AgentWebUrlKind.DEEPSEEK_HARNESS,
                transcript = "http://192.168.1.20:3080",
            ),
        )
    }

    @Test
    fun launchPayloadDoesNotExposeTheBrowserUrl() {
        val payload = AgentWebLaunchResult(
            ok = true,
            code = "OPENED",
            packageId = "kimi",
        ).toPayload()

        assertTrue(payload["ok"] == true)
        assertFalse(payload.containsKey("url"))
        assertFalse(payload.containsValue("#token=secret"))
    }
}
