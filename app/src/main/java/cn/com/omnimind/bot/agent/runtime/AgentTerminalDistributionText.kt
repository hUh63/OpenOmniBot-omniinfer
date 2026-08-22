package cn.com.omnimind.bot.agent

import com.rk.terminal.runtime.TerminalDistribution

/** Keeps every distribution name exposed to the model tied to one runtime snapshot. */
object AgentTerminalDistributionText {
    const val NAME_TOKEN = "{{OMNIBOT_TERMINAL_DISTRIBUTION}}"
    const val ID_TOKEN = "{{OMNIBOT_TERMINAL_DISTRIBUTION_ID}}"

    fun resolve(text: String, distribution: TerminalDistribution.Spec): String {
        return text
            .replace(NAME_TOKEN, distribution.displayName)
            .replace(ID_TOKEN, distribution.id)
    }

    fun makeDistributionExplicit(
        text: String,
        distribution: TerminalDistribution.Spec,
        english: Boolean
    ): String {
        val name = distribution.displayName
        val explicitChinese = resolve(text, distribution)
            .replace("内嵌终端环境", "内嵌 $name 环境")
            .replace("终端环境", "$name 环境")
            .replace("终端会话", "$name 会话")
            .replace("终端命令", "$name 命令")
            .replace("终端输出", "$name 输出")
            .replace("终端 session", "$name session")
            .replace("终端工具", "$name 工具")
        return if (english) {
            explicitChinese
                .replace("embedded terminal environment", "embedded $name environment", ignoreCase = true)
                .replace("terminal environment", "$name environment", ignoreCase = true)
                .replace("terminal session", "$name session", ignoreCase = true)
                .replace("terminal command", "$name command", ignoreCase = true)
                .replace("terminal output", "$name output", ignoreCase = true)
                .replace("terminal tool", "$name tool", ignoreCase = true)
        } else {
            explicitChinese
        }
    }
}
