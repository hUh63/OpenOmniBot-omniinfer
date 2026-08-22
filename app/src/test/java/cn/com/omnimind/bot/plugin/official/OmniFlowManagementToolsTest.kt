package cn.com.omnimind.bot.plugin.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OmniFlowManagementToolsTest {
    @Test
    fun `plugin exposes the complete Function and RunLog management surface`() {
        val definitions = OmniFlowManagementTools.definitions()

        assertEquals(OmniFlowManagementTools.TOOL_NAMES, definitions.mapTo(linkedSetOf()) { it.name })
        assertEquals(OmniFlowManagementTools.TOOL_NAMES.size, definitions.size)
        definitions.forEach { definition ->
            assertFalse(definition.description.isBlank())
            assertEquals("object", definition.parameters["type"]?.toString()?.trim('"'))
        }
    }
}
