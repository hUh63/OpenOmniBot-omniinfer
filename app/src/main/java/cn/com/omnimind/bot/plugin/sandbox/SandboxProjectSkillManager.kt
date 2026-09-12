package cn.com.omnimind.bot.plugin.sandbox

import android.content.Context
import cn.com.omnimind.bot.agent.AgentWorkspaceManager
import cn.com.omnimind.bot.agent.SkillIndexService
import cn.com.omnimind.bot.plugin.OmniPluginToolDefinition
import java.io.File
import java.util.UUID

interface SandboxProjectSkillManager {
    fun install(
        sourceDirectory: File,
        skillId: String,
        tools: List<OmniPluginToolDefinition>,
    )

    fun setEnabled(skillId: String, enabled: Boolean)

    fun uninstall(skillId: String)
}

internal object NoOpSandboxProjectSkillManager : SandboxProjectSkillManager {
    override fun install(
        sourceDirectory: File,
        skillId: String,
        tools: List<OmniPluginToolDefinition>,
    ) = Unit

    override fun setEnabled(skillId: String, enabled: Boolean) = Unit

    override fun uninstall(skillId: String) = Unit
}

internal class AndroidSandboxProjectSkillManager(context: Context) : SandboxProjectSkillManager {
    private val appContext = context.applicationContext
    private val workspaceManager = AgentWorkspaceManager(appContext)
    private val skillIndex = SkillIndexService(appContext, workspaceManager)
    private val stagingRoot = File(appContext.cacheDir, "sandbox-plugin-skills")

    override fun install(
        sourceDirectory: File,
        skillId: String,
        tools: List<OmniPluginToolDefinition>,
    ) {
        val targetDirectoryName = "local-project-$skillId"
        val targetDirectory = File(workspaceManager.skillsRoot(), targetDirectoryName).canonicalFile
        val collision = skillIndex.listSkillsForManagement().firstOrNull { entry ->
            entry.installed && entry.id == skillId && File(entry.rootPath).canonicalFile != targetDirectory
        }
        require(collision == null) {
            "A non-plugin skill already uses id $skillId"
        }
        val staging = File(stagingRoot, UUID.randomUUID().toString())
        try {
            copyTree(sourceDirectory.canonicalFile, staging)
            val skillFile = File(staging, "SKILL.md")
            skillFile.appendText(generatedToolSection(tools))
            val installed = skillIndex.installSkillFromDirectory(
                sourcePath = staging.absolutePath,
                targetDirectoryName = targetDirectoryName,
            )
            require(installed.id == skillId) {
                "Installed skill id ${installed.id} does not match project id $skillId"
            }
        } finally {
            staging.deleteRecursively()
            stagingRoot.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
        }
    }

    override fun setEnabled(skillId: String, enabled: Boolean) {
        val entry = skillIndex.listSkillsForManagement().firstOrNull { candidate ->
            candidate.installed && candidate.id == skillId &&
                File(candidate.rootPath).name == "local-project-$skillId"
        } ?: throw IllegalArgumentException("Plugin skill is not installed: $skillId")
        skillIndex.setSkillEnabled(entry.id, enabled)
    }

    override fun uninstall(skillId: String) {
        val entry = skillIndex.listSkillsForManagement().firstOrNull { candidate ->
            candidate.installed && candidate.id == skillId &&
                File(candidate.rootPath).name == "local-project-$skillId"
        } ?: return
        check(skillIndex.deleteSkill(entry.id)) { "Unable to remove plugin skill: $skillId" }
    }

    private fun generatedToolSection(tools: List<OmniPluginToolDefinition>): String = buildString {
        append("\n\n## Xiaowan Tools\n\n")
        append("Call these tools directly when the user's request matches this skill. ")
        append("Do not open the dashboard and simulate taps for these actions.\n\n")
        tools.forEach { tool ->
            append("- `")
            append(tool.name)
            append("`: ")
            append(tool.description.trim())
            append('\n')
        }
    }

    private fun copyTree(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles().orEmpty().forEach { child ->
                copyTree(child, File(target, child.name))
            }
            return
        }
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
    }
}
