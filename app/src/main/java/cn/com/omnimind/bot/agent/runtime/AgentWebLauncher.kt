package cn.com.omnimind.bot.agent.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import cn.com.omnimind.bot.agent.AgentWorkspaceManager
import cn.com.omnimind.bot.terminal.EmbeddedTerminalRuntime
import cn.com.omnimind.bot.terminal.ReTerminalSessionBridge
import com.ai.assistance.operit.terminal.TerminalManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Web surfaces that are backed by an installed local Agent runtime. */
internal enum class AgentWebService(
    val agentId: String,
    val packageId: String,
    val commandName: String,
    val command: String,
    val sessionId: String,
    val urlKind: AgentWebUrlKind,
) {
    KIMI(
        agentId = AcpAgentProfileStore.KIMI_CODE_AGENT_ID,
        packageId = "kimi",
        commandName = "kimi",
        command = "kimi web --no-open",
        sessionId = "omnibot-web-kimi",
        urlKind = AgentWebUrlKind.KIMI,
    ),
    DEEPSEEK_HARNESS(
        agentId = AcpAgentProfileStore.DEEPSEEK_HARNESS_AGENT_ID,
        packageId = "deepseek_harness",
        commandName = "dsh",
        command = "dsh web --no-open",
        sessionId = "omnibot-web-dsh",
        urlKind = AgentWebUrlKind.DEEPSEEK_HARNESS,
    );

    companion object {
        fun forAgentId(agentId: String): AgentWebService? {
            val normalized = agentId.trim()
            return entries.firstOrNull { it.agentId == normalized }
        }
    }
}

internal enum class AgentWebUrlKind {
    KIMI,
    DEEPSEEK_HARNESS,
}

internal data class AgentWebLaunchRequest(
    val service: AgentWebService,
    val environment: Map<String, String>,
)

internal data class AgentWebLaunchResult(
    val ok: Boolean,
    val code: String,
    val packageId: String,
    val reused: Boolean = false,
    val error: String? = null,
) {
    fun toPayload(): Map<String, Any?> = linkedMapOf(
        "ok" to ok,
        "code" to code,
        "packageId" to packageId,
        "reused" to reused,
        "error" to error,
    ).filterValues { it != null }
}

/**
 * Starts a vendor Web UI in a named ReTerminal background session and opens
 * only the vendor-published loopback URL in the system browser.
 *
 * The browser URL is deliberately never returned through Flutter: Kimi's
 * URL contains a bearer token and the DSH URL is still a privileged local
 * filesystem/shell surface.
 */
internal class AgentWebLauncher(
    private val context: Context,
) {
    private val launchMutexes = AgentWebService.entries.associateWith { Mutex() }

    suspend fun launch(request: AgentWebLaunchRequest): AgentWebLaunchResult =
        launchMutexes.getValue(request.service).withLock {
            withContext(Dispatchers.IO) {
                val service = request.service
                if (!isCommandAvailable(service.commandName)) {
                    return@withContext AgentWebLaunchResult(
                        ok = false,
                        code = CODE_RUNTIME_MISSING,
                        packageId = service.packageId,
                        error = "Web runtime is not installed.",
                    )
                }

                val launch = try {
                    EmbeddedTerminalRuntime.launchBackgroundServiceSession(
                        context = context,
                        sessionId = service.sessionId,
                        command = "$MANAGED_NPM_PATH_PREFIX exec ${service.command}",
                        workingDirectory = AgentWorkspaceManager.SHELL_ROOT_PATH,
                        environment = request.environment,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    return@withContext AgentWebLaunchResult(
                        ok = false,
                        code = CODE_START_FAILED,
                        packageId = service.packageId,
                        error = "Unable to start the Web service.",
                    )
                }
                if (!launch.started && !launch.alreadyRunning) {
                    return@withContext AgentWebLaunchResult(
                        ok = false,
                        code = CODE_START_FAILED,
                        packageId = service.packageId,
                        error = "Unable to start the Web service.",
                    )
                }

                val url = awaitUrl(service)
                    ?: return@withContext AgentWebLaunchResult(
                        ok = false,
                        code = CODE_URL_TIMEOUT,
                        packageId = service.packageId,
                        reused = launch.alreadyRunning,
                        error = "The Web service did not publish a local URL.",
                    )

                val opened = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }.isSuccess
                if (!opened) {
                    AgentWebLaunchResult(
                        ok = false,
                        code = CODE_BROWSER_UNAVAILABLE,
                        packageId = service.packageId,
                        reused = launch.alreadyRunning,
                        error = "No system browser is available.",
                    )
                } else {
                    AgentWebLaunchResult(
                        ok = true,
                        code = CODE_OPENED,
                        packageId = service.packageId,
                        reused = launch.alreadyRunning,
                    )
                }
            }
        }

    private suspend fun isCommandAvailable(commandName: String): Boolean {
        val result = TerminalManager.getInstance(context).executeHiddenCommand(
            command = "$MANAGED_NPM_PATH_PREFIX command -v ${shellQuote(commandName)}",
            executorKey = "agent-web-command-probe-$commandName",
            timeoutMs = COMMAND_PROBE_TIMEOUT_MS,
        )
        return result.isOk && result.exitCode == 0
    }

    private suspend fun awaitUrl(service: AgentWebService): String? {
        repeat(URL_WAIT_ATTEMPTS) { attempt ->
            val session = ReTerminalSessionBridge.getSession(context, service.sessionId)
            val transcript = session?.getTranscriptText().orEmpty()
            AgentWebUrlParser.find(service.urlKind, transcript)?.let { return it }
            if (attempt > 1 && session != null && !session.isRunning) {
                return null
            }
            delay(URL_WAIT_INTERVAL_MS)
        }
        return null
    }

    private companion object {
        const val CODE_OPENED = "OPENED"
        const val CODE_RUNTIME_MISSING = "RUNTIME_MISSING"
        const val CODE_START_FAILED = "START_FAILED"
        const val CODE_URL_TIMEOUT = "URL_TIMEOUT"
        const val CODE_BROWSER_UNAVAILABLE = "BROWSER_UNAVAILABLE"
        const val COMMAND_PROBE_TIMEOUT_MS = 15_000L
        const val URL_WAIT_ATTEMPTS = 60
        const val URL_WAIT_INTERVAL_MS = 500L
        const val MANAGED_NPM_PATH_PREFIX =
            "PATH=\"/root/.npm-global/bin:\$PATH\"; export PATH;"

        fun shellQuote(value: String): String =
            "'${value.replace("'", "'\"'\"'")}'"
    }
}

internal object AgentWebUrlParser {
    private val ansiEscapeRegex = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
    private val kimiUrlRegex = Regex(
        "https?://(?:127\\.0\\.0\\.1|localhost):\\d+/#token=[A-Za-z0-9_-]+",
    )
    private val deepSeekUrlRegex = Regex(
        "https?://(?:127\\.0\\.0\\.1|localhost):\\d+",
    )

    fun find(kind: AgentWebUrlKind, transcript: String): String? {
        val clean = ansiEscapeRegex.replace(transcript, "")
        val regex = when (kind) {
            AgentWebUrlKind.KIMI -> kimiUrlRegex
            AgentWebUrlKind.DEEPSEEK_HARNESS -> deepSeekUrlRegex
        }
        return regex.findAll(clean).lastOrNull()?.value
    }
}
