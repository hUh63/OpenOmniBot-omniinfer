package cn.com.omnimind.bot.omniflow

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.assists.controller.http.HttpController
import cn.com.omnimind.assists.controller.http.SceneChatCompletionResponse
import cn.com.omnimind.baselib.llm.ChatCompletionRequest
import cn.com.omnimind.bot.terminal.EmbeddedTerminalRuntime
import cn.com.omnimind.bot.plugin.runtime.RuntimeSkillBundleManager
import com.ai.assistance.operit.terminal.TerminalManager
import com.rk.terminal.runtime.TerminalDistribution
import com.rk.terminal.runtime.UbuntuRepositoryManager
import java.util.UUID

internal class OmniFlowAppPlatform(
    private val runtimeSkills: RuntimeSkillBundleManager,
) : OmniFlowPlatform {
    private companion object {
        const val TAG = "[OmniFlowAppPlatform]"
        const val PREFS_NAME = "omniflow_python_runtime"
        const val READY_VERSION_KEY = "python_ready_version"
    }

    override suspend fun startProcess(
        context: Context,
        command: String,
        environment: Map<String, String>,
    ): Process = TerminalManager.getInstance(context.applicationContext)
        .startLongLivedProcess(
            command = command,
            executorKey = "omniflow-${UUID.randomUUID()}",
            redirectErrorStream = false,
            extraEnvironment = environment,
        )

    override suspend fun ensurePython(context: Context, expectedVersion: String) {
        val appContext = context.applicationContext
        val terminalStartedAt = System.currentTimeMillis()
        val terminalStatus = EmbeddedTerminalRuntime.warmup(appContext)
        log(
            "terminal_ready initialized=${terminalStatus.initialized} " +
                "durationMs=${System.currentTimeMillis() - terminalStartedAt}",
        )
        require(terminalStatus.success && terminalStatus.initialized) {
            terminalStatus.message.ifBlank { "omniflow_terminal_runtime_unavailable" }
        }
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val distribution = TerminalDistribution.selected()
        val environmentVersion = "$expectedVersion+system-numpy-v3+${distribution.id}"
        if (prefs.getString(READY_VERSION_KEY, null) == environmentVersion) {
            log("python_ready_cached version=$environmentVersion")
            return
        }
        val startedAt = System.currentTimeMillis()
        log("python_probe_start version=$expectedVersion")
        val command = buildOmniFlowPythonPrepareCommand(
            expectedVersion = expectedVersion,
            distributionId = distribution.id,
            ubuntuRepositorySetup = UbuntuRepositoryManager.buildSelectedRepositorySetupCommand(),
        )
        val result = TerminalManager.getInstance(appContext).executeHiddenCommand(
            command = command,
            executorKey = "omniflow-python-runtime",
            timeoutMs = 5 * 60_000L,
            onOutputChunk = { chunk ->
                chunk.lineSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach { line -> log("python_prepare_output $line") }
            },
        )
        require(result.isOk && result.exitCode == 0) {
            result.error.takeIf(String::isNotBlank)
                ?: result.output.takeLast(800).trim()
                    .ifBlank { "omniflow_python_runtime_not_preinstalled" }
        }
        prefs.edit().putString(READY_VERSION_KEY, environmentVersion).apply()
        log(
            "python_probe_ready version=$expectedVersion " +
                "durationMs=${System.currentTimeMillis() - startedAt}",
        )
    }

    private fun log(message: String) {
        runCatching { OmniLog.i(TAG, message) }
    }

    override suspend fun resolveRuntimeSkill(
        context: Context,
        refresh: Boolean,
    ): OmniFlowSkillLocation {
        val location = runtimeSkills.resolve(refresh)
        return OmniFlowSkillLocation(
            androidRoot = location.androidRoot,
            shellRoot = location.shellRoot,
            source = location.source,
        )
    }

    override suspend fun resolvePackagedRuntimeSkill(context: Context): OmniFlowSkillLocation {
        val location = runtimeSkills.resolvePackaged(refresh = true)
        return OmniFlowSkillLocation(
            androidRoot = location.androidRoot,
            shellRoot = location.shellRoot,
            source = location.source,
        )
    }

    override fun allowsPackagedRuntimeFallback(): Boolean =
        runtimeSkills.allowsPackagedFallback()

    override suspend fun bootstrapRuntimeSkill(
        context: Context,
        location: OmniFlowSkillLocation,
    ): OmniFlowSkillLocation {
        val ready = runtimeSkills.bootstrap(
            cn.com.omnimind.bot.plugin.runtime.RuntimeSkillLocation(
                androidRoot = location.androidRoot,
                shellRoot = location.shellRoot,
                source = location.source,
                staged = location.source == "market-pending",
            )
        )
        return OmniFlowSkillLocation(
            androidRoot = ready.androidRoot,
            shellRoot = ready.shellRoot,
            source = ready.source,
        )
    }

    override suspend fun reclaimRuntimeSkill(context: Context) {
        runtimeSkills.reclaim()
    }

    override suspend fun completeJson(request: ChatCompletionRequest): String {
        val response = HttpController.postSceneChatCompletion(request)
        return resolveOmniFlowJsonCompletion(response)
    }

}

internal fun buildOmniFlowPythonPrepareCommand(
    expectedVersion: String,
    distributionId: String = "alpine",
    ubuntuRepositorySetup: String = ":",
): String {
    require(Regex("""\d+\.\d+""").matches(expectedVersion)) {
        "invalid_python_version"
    }
    require(distributionId == "alpine" || distributionId == "ubuntu") {
        "unsupported_terminal_distribution"
    }
    val repairCommand = if (distributionId == "ubuntu") {
        """
            $ubuntuRepositorySetup
            apt-get update
            DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 python3-pip python3-numpy
        """.trimIndent()
    } else {
        "apk --wait 300 add --no-cache python3 py3-pip py3-numpy"
    }
    return """
        set -e
        expected='$expectedVersion'
        echo 'OMNIFLOW_PYTHON_STAGE=probe_start'
        base_packages_ready() {
          command -v python3 >/dev/null 2>&1 &&
          python3 -c 'import sys; print("%d.%d" % sys.version_info[:2])' | grep -qx "${'$'}expected" &&
          python3 -c 'import numpy' >/dev/null 2>&1
        }
        if ! base_packages_ready; then
          echo 'OMNIFLOW_PYTHON_STAGE=repair_start package=python-numpy'
          $repairCommand
          printf '%s\n' '$distributionId-python$expectedVersion-numpy-v1' > /etc/omnibot-python-environment
          echo 'OMNIFLOW_PYTHON_STAGE=repair_ready package=python-numpy'
        else
          echo 'OMNIFLOW_PYTHON_STAGE=probe_ready source=environment'
        fi
        base_packages_ready
        echo 'OMNIFLOW_PYTHON_STAGE=ready'
    """.trimIndent()
}

internal fun resolveOmniFlowJsonCompletion(response: SceneChatCompletionResponse): String {
    check(response.success) { response.message.ifBlank { "model_completion_failed" } }
    val toolCall = response.toolCalls.singleOrNull {
        it.function.name == "submit_json"
    } ?: error("model_completion_submit_json_required")
    return toolCall.function.arguments.trim().ifBlank {
        error("model_completion_submit_json_empty")
    }
}
