package cn.com.omnimind.bot.mcp

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerStateTest {
    @Test
    fun `HTTP payload preserves mixed state field types`() {
        val payload = McpServerState(
            enabled = true,
            running = true,
            host = "10.0.2.15",
            port = 8899,
            token = "secret",
        ).toJsonObject()

        assertTrue(payload.getValue("enabled").jsonPrimitive.boolean)
        assertTrue(payload.getValue("running").jsonPrimitive.boolean)
        assertEquals("10.0.2.15", payload.getValue("host").jsonPrimitive.content)
        assertEquals(8899, payload.getValue("port").jsonPrimitive.int)
        assertEquals("secret", payload.getValue("token").jsonPrimitive.content)
    }
}
