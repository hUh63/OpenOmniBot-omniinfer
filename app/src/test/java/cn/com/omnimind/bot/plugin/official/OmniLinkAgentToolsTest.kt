package cn.com.omnimind.bot.plugin.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniLinkAgentToolsTest {
    @Test
    fun `plugin exposes generic collaboration primitives`() {
        val definitions = OmniLinkAgentTools.definitions()

        assertEquals(OmniLinkAgentTools.TOOL_NAMES, definitions.mapTo(linkedSetOf()) { it.name })
        assertEquals(3, definitions.size)
        assertTrue(
            definitions.first { it.name == OmniLinkAgentTools.DEVICES }
                .description.contains("电量"),
        )
        val controlTool = definitions.first {
            it.name == OmniLinkAgentTools.CONTROL
        }
        assertTrue(controlTool.parameters["required"].toString().contains("action"))
        assertTrue(controlTool.parameters.toString().contains("input"))
        val eventTool = definitions.first { it.name == OmniLinkAgentTools.EVENTS }
        assertTrue(eventTool.parameters.toString().contains("subscribe"))
        assertTrue(eventTool.parameters.toString().contains("stop"))
    }
}
