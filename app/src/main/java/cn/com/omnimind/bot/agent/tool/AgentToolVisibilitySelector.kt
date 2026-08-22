package cn.com.omnimind.bot.agent

object AgentToolVisibilitySelector {
    @Suppress("UNUSED_PARAMETER")
    fun select(
        userMessage: String,
        candidates: List<ToolCandidate>,
        routingMode: AgentToolRoutingMode = AgentToolRoutingMode.DEFAULT,
    ): Set<String> = candidates.mapTo(linkedSetOf()) { it.name }

    data class ToolCandidate(
        val name: String,
        val displayName: String,
        val description: String,
        val owner: String? = null,
        val dynamic: Boolean = false,
    )
}

enum class AgentToolRoutingMode {
    DEFAULT,
    WORKSPACE_DIRECT;

    companion object {
        private const val FRONTMATTER_KEY = "tool-routing"
        private const val WORKSPACE_DIRECT_VALUE = "workspace-direct"

        fun fromSkillFrontmatter(
            frontmatter: Iterable<Map<String, String>>,
        ): AgentToolRoutingMode = if (frontmatter.any { values ->
            values[FRONTMATTER_KEY]?.trim()?.equals(
                WORKSPACE_DIRECT_VALUE,
                ignoreCase = true,
            ) == true
        }) {
            WORKSPACE_DIRECT
        } else {
            DEFAULT
        }
    }
}
