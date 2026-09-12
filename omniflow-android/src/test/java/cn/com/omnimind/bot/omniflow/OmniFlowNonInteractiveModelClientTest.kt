package cn.com.omnimind.bot.omniflow

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniFlowNonInteractiveModelClientTest {
    @Test
    fun `function registration is a non interactive management tool`() {
        val source = projectSource(
            "omniflow-android/src/main/java/cn/com/omnimind/bot/omniflow/OmniFlow.kt",
        )
        val tools = source
            .substringAfter("private val NON_INTERACTIVE_TOOL_NAMES = setOf(")
            .substringBefore("\n    )")

        assertTrue(tools.contains("\"save_function\""))
    }

    @Test
    fun `non interactive tools retain the model host for offline enhancement`() {
        val source = projectSource(
            "omniflow-android/src/main/java/cn/com/omnimind/bot/omniflow/OmniFlow.kt",
        )
        val branch = source
            .substringAfter("if (toolCall.name in NON_INTERACTIVE_TOOL_NAMES)")
            .substringBefore("val startedAtMs")

        assertTrue(branch.contains("modelClient = modelClient"))
    }

    private fun projectSource(path: String): String {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (!current.resolve("settings.gradle.kts").isFile) {
            current = current.parentFile ?: error("Could not locate project root")
        }
        return current.resolve(path).readText()
    }
}
