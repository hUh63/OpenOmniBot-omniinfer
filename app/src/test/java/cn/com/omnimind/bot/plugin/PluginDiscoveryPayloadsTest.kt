package cn.com.omnimind.bot.plugin

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDiscoveryPayloadsTest {

    @Test
    fun `agent discovery filters installed plugins without exposing install actions`() {
        val available = state("com.omnimind.available", installed = false, enabled = false)
        val installed = state("com.omnimind.installed", installed = true, enabled = true)

        val payload = PluginDiscoveryPayloads.list(listOf(available, installed), installedOnly = true)

        assertEquals(1, payload.getValue("count").jsonPrimitive.content.toInt())
        assertEquals(
            "com.omnimind.installed",
            payload.getValue("plugins").jsonArray.single().jsonObject
                .getValue("id").jsonPrimitive.content,
        )
        assertTrue(payload.containsKey("install_policy"))
        assertFalse(payload.containsKey("install"))
    }

    @Test
    fun `agent discovery details expose capabilities and status`() {
        val plugin = state("com.omnimind.omni-vlm-lite", installed = true, enabled = false)

        val payload = requireNotNull(PluginDiscoveryPayloads.get(listOf(plugin), plugin.descriptor.id))

        assertEquals("Android GUI", payload.getValue("capabilities").jsonArray.single().jsonPrimitive.content)
        assertEquals("false", payload.getValue("enabled").jsonPrimitive.content)
        assertEquals("runtime_bundle", payload.getValue("kind").jsonPrimitive.content)
    }

    @Test
    fun `agent discovery omits hidden core plugins`() {
        val hidden = state(
            id = "com.omnimind.vibe-project-builder",
            installed = false,
            enabled = false,
            presentation = buildJsonObject { put("visibility", "hidden") },
        )

        val payload = PluginDiscoveryPayloads.list(listOf(hidden), installedOnly = false)

        assertEquals(0, payload.getValue("count").jsonPrimitive.content.toInt())
        assertEquals(null, PluginDiscoveryPayloads.get(listOf(hidden), hidden.descriptor.id))
    }

    private fun state(
        id: String,
        installed: Boolean,
        enabled: Boolean,
        presentation: JsonObject = JsonObject(emptyMap()),
    ) = OmniPluginState(
        descriptor = OmniPluginDescriptor(
            id = id,
            name = id.substringAfterLast('.'),
            version = "1.0.0",
            description = "test plugin",
            publisher = "OmniMind",
            capabilities = listOf("Android GUI"),
            presentation = presentation,
        ),
        installed = installed,
        enabled = enabled,
        compatible = true,
    )
}
