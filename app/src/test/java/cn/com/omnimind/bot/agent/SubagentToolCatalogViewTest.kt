package cn.com.omnimind.bot.agent

import cn.com.omnimind.baselib.llm.ChatCompletionFunction
import cn.com.omnimind.baselib.llm.ChatCompletionTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SubagentToolCatalogView] narrows the parent catalog down to a role whitelist;
 * it never adds capabilities. The roles live in [SubagentProfileRegistry], and
 * this pins the narrowing rules for `explorer`, `memory-curator` and `planner`.
 */
class SubagentToolCatalogViewTest {
    @Test
    fun `explorer keeps read-only and observation tools only`() {
        val parent = FakeCatalog(
            listOf(
                tool("file_read", "workspace"),
                tool("memory_search", "memory"),
                tool("browser_use", "browser"),
                // Writes are not part of the observer role.
                tool("memory_write_daily", "memory"),
                // Not in the role table at all.
                tool("terminal_execute", "builtin"),
                // Same name, but served by a remote MCP server.
                tool("memory_load", "memory", serverName = "remote-mcp"),
            )
        )

        val view = SubagentToolCatalogView(parent, SubagentProfileRegistry.explorer.id)

        assertEquals(
            setOf("file_read", "memory_search", "browser_use"),
            view.toolsForModel.map { it.function.name }.toSet()
        )
    }

    @Test
    fun `memory-curator keeps memory and file tools but drops browser and skill tools`() {
        val parent = FakeCatalog(
            listOf(
                tool("file_read", "workspace"),
                tool("memory_search", "memory"),
                tool("memory_write_daily", "memory"),
                tool("browser_use", "browser"),
                tool("skills_list", "skill"),
            )
        )

        val view = SubagentToolCatalogView(parent, SubagentProfileRegistry.memoryCurator.id)

        assertEquals(
            setOf("file_read", "memory_search", "memory_write_daily"),
            view.toolsForModel.map { it.function.name }.toSet()
        )
    }

    @Test
    fun `a tool whose type disagrees with its role entry is dropped`() {
        val parent = FakeCatalog(
            listOf(
                // The role table maps browser_use to the browser type.
                tool("browser_use", "builtin"),
                tool("file_read", "workspace"),
            )
        )

        val view = SubagentToolCatalogView(parent, SubagentProfileRegistry.explorer.id)

        assertEquals(
            setOf("file_read"),
            view.toolsForModel.map { it.function.name }.toSet()
        )
    }

    @Test
    fun `planner exposes no tools at all`() {
        val parent = FakeCatalog(
            listOf(
                tool("file_read", "workspace"),
                tool("browser_use", "browser"),
            )
        )

        val view = SubagentToolCatalogView(parent, SubagentProfileRegistry.planner.id)

        assertTrue(view.toolsForModel.isEmpty())
    }

    @Test
    fun `explorer may observe through the browser but not mutate remote state`() {
        val parent = FakeCatalog(listOf(tool("browser_use", "browser")))
        val view = SubagentToolCatalogView(parent, SubagentProfileRegistry.explorer.id)

        view.validateArguments("browser_use", browserAction("get_text"))
        view.validateArguments("browser_use", browserAction("navigate"))

        val rejected = runCatching {
            view.validateArguments("browser_use", browserAction("click"))
        }.exceptionOrNull()
        assertTrue(rejected is IllegalArgumentException)

        // Tools outside the role are rejected before they ever reach the parent.
        val outsideRole = runCatching {
            view.validateArguments("memory_write_daily", JsonObject(emptyMap()))
        }.exceptionOrNull()
        assertTrue(outsideRole is IllegalArgumentException)
    }

    private fun browserAction(action: String) =
        JsonObject(mapOf("action" to JsonPrimitive(action)))

    private fun tool(name: String, toolType: String, serverName: String? = null) =
        Triple(name, toolType, serverName)

    private class FakeCatalog(
        private val tools: List<Triple<String, String, String?>>
    ) : AgentToolCatalog {
        override val toolsForModel = tools.map { (name, _, _) ->
            ChatCompletionTool(
                function = ChatCompletionFunction(name = name)
            )
        }

        override fun runtimeDescriptor(
            toolName: String
        ): AgentToolRegistry.RuntimeToolDescriptor {
            val (name, toolType, serverName) = tools.first { it.first == toolName }
            return AgentToolRegistry.RuntimeToolDescriptor(
                name = name,
                displayName = name,
                toolType = toolType,
                serverName = serverName
            )
        }

        override fun validateArguments(toolName: String, arguments: JsonObject) = Unit
    }
}
