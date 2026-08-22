package cn.com.omnimind.bot.agent

import cn.com.omnimind.baselib.i18n.PromptLocale
import com.rk.terminal.runtime.TerminalDistribution
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTerminalToolDefinitionTest {
    @Test
    fun progressTextUsesSelectedDistributionInsteadOfGenericTerminalLabel() {
        assertTrue(
            AgentTerminalDistributionText.makeDistributionExplicit(
                "正在调用内嵌终端环境执行命令",
                TerminalDistribution.ubuntu,
                english = false
            ).contains("内嵌 Ubuntu 环境")
        )
        assertTrue(
            AgentTerminalDistributionText.makeDistributionExplicit(
                "Terminal command failed",
                TerminalDistribution.alpine,
                english = true
            ).contains("Alpine command failed")
        )
    }

    @Test
    fun ubuntuDefinitionsExposeOnlyUbuntuAndMatchingDistroId() {
        val text = terminalDefinitionsText(TerminalDistribution.ubuntu)

        assertTrue(text.contains("Ubuntu"))
        assertTrue(text.contains("prootDistro=ubuntu"))
        assertFalse(text.contains("Alpine"))
        assertFalse(text.contains("OMNIBOT_TERMINAL_DISTRIBUTION"))
        assertFalse(text.contains("terminal environment"))
    }

    @Test
    fun alpineDefinitionsExposeOnlyAlpineAndMatchingDistroId() {
        val text = terminalDefinitionsText(TerminalDistribution.alpine)

        assertTrue(text.contains("Alpine"))
        assertTrue(text.contains("prootDistro=alpine"))
        assertFalse(text.contains("Ubuntu"))
        assertFalse(text.contains("OMNIBOT_TERMINAL_DISTRIBUTION"))
        assertFalse(text.contains("terminal environment"))
    }

    private fun terminalDefinitionsText(distribution: TerminalDistribution.Spec): String {
        return AgentToolDefinitions.staticTools(PromptLocale.EN_US, distribution)
            .filter { definition ->
                val function = definition["function"] as? JsonObject
                function?.get("name")?.jsonPrimitive?.content?.startsWith("terminal_") == true
            }
            .joinToString("\n")
    }
}
