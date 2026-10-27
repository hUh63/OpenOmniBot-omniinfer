package cn.com.omnimind.bot.agent

import cn.com.omnimind.baselib.llm.ChatCompletionMessage
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Spawns and supervises real subagents.
 *
 * Each task gets:
 *  - its own AgentOrchestrator instance using the parent harness's existing
 *    tool catalog
 *  - the parent runtime owns lifecycle and cancellation; no implicit round or token cap
 *
 * Parent cancellation propagates naturally through structured concurrency:
 * if the parent's tool call is cancelled, [supervisorScope] tears down every
 * in-flight subagent's coroutine.
 *
 * NOTE: We deliberately reuse the parent's existing [AgentToolExecutor]
 * router rather than constructing a new one. The router may own terminal,
 * browser, or plugin resources, so child orchestrators are explicitly
 * non-owners and must never dispose it. The lazy provider is required because
 * SubagentToolHandler → SubagentDispatcher → router is a construction-time
 * cycle that we break with deferred lookup.
 */
/** 单个子任务的时间预算（见 dispatch 里的 withTimeoutOrNull）。 */
private const val SINGLE_SUBAGENT_TIMEOUT_MS = 10L * 60L * 1000L

/**
 * 子 agent 允许的嵌套层数。
 * 父 catalog 里包含 subagent_dispatch 自身，模型一旦自问自答式拆任务就会递归；
 * 每层还能各自并发 4 个、单个最长 10 分钟，深度 3 就是 64 套完整 orchestrator
 * （各自带 tool catalog 和多轮 LLM）。这里硬性封顶，超出直接拒绝并告知模型。
 */
private const val MAX_SUBAGENT_DEPTH = 2

/** 从 agentRunId 反解嵌套层数：主 agent 为 0，`subagent-1-xxxx` 为 1。 */
internal fun subagentDepthOf(agentRunId: String): Int {
    val rest = agentRunId.removePrefix("subagent-")
    if (rest == agentRunId) return 0
    return rest.substringBefore('-').toIntOrNull() ?: 1
}

class SubagentDispatcher(
    private val llmClient: AgentLlmClient,
    private val toolExecutorProvider: () -> AgentToolExecutor,
    private val parentCatalogProvider: () -> AgentToolCatalog,
    private val eventAdapter: AgentEventAdapter,
    private val model: String,
    private val toolImageContinuationPolicy: AgentToolImageContinuationPolicy =
        AgentToolImageContinuationPolicy.DEFAULT,
    private val contextCompactorFactory: (() -> AgentContextCompactionController)? = null
) {

    data class SubagentTaskSpec(
        val profileId: String,
        val instruction: String
    )

    data class SubagentRunResult(
        val subagentId: String,
        val profileId: String,
        val taskIndex: Int,
        val status: String,
        val finalContent: String,
        val toolCallSummaries: List<String>,
        val errorMessage: String? = null
    )

    data class SubagentProgressEvent(
        val sequence: Long,
        val createdAtMs: Long,
        val kind: String,
        val summary: String,
        val taskIndex: Int? = null,
        val subagentId: String? = null,
        val profileId: String? = null,
        val status: String = "running",
        val toolName: String? = null
    ) {
        fun toPayload(): Map<String, Any?> {
            return linkedMapOf<String, Any?>(
                "id" to "subagent-event-$sequence",
                "seq" to sequence,
                "createdAt" to createdAtMs,
                "kind" to kind,
                "summary" to summary,
                "taskIndex" to taskIndex,
                "subagentId" to subagentId,
                "profileId" to profileId,
                "status" to status,
                "toolName" to toolName
            ).filterValues { it != null }
        }
    }

    suspend fun dispatch(
        parentEnv: AgentExecutionEnvironment,
        tasks: List<SubagentTaskSpec>,
        concurrency: Int,
        progressReporter: (suspend (SubagentProgressEvent) -> Unit)? = null
    ): List<SubagentRunResult> {
        if (tasks.isEmpty()) return emptyList()
        require(concurrency > 0) { "concurrency must be positive" }
        val limit = concurrency
        val progressSequence = AtomicLong(0)
        emitProgress(
            progressReporter,
            progressSequence,
            kind = "dispatch_started",
            summary = "正在分派 ${tasks.size} 个子任务（并发 $limit）"
        )
        val semaphore = Semaphore(limit)
        return supervisorScope {
            tasks.mapIndexed { index, spec ->
                async {
                    semaphore.withPermit {
                        // 子弹窗原先没有任何超时：一个卡住的子 agent（LLM 不响应、
                        // 工具挂死）会让父回合的 subagent_dispatch 永不返回，
                        // 整轮 agent 直接挂死，只能靠用户取消。
                        withTimeoutOrNull(SINGLE_SUBAGENT_TIMEOUT_MS) {
                            runSingleSubagent(
                                parentEnv = parentEnv,
                                taskIndex = index,
                                spec = spec,
                                progressReporter = progressReporter,
                                progressSequence = progressSequence
                            )
                        } ?: SubagentRunResult(
                            subagentId = "subagent-timeout-${UUID.randomUUID().toString().take(8)}",
                            profileId = spec.profileId,
                            taskIndex = index,
                            status = "timeout",
                            finalContent = "",
                            toolCallSummaries = emptyList(),
                            errorMessage =
                                "子任务超时（${SINGLE_SUBAGENT_TIMEOUT_MS / 60_000L} 分钟）"
                        )
                    }
                }
            }.awaitAll().sortedBy { it.taskIndex }
        }
    }

    private suspend fun runSingleSubagent(
        parentEnv: AgentExecutionEnvironment,
        taskIndex: Int,
        spec: SubagentTaskSpec,
        progressReporter: (suspend (SubagentProgressEvent) -> Unit)?,
        progressSequence: AtomicLong
    ): SubagentRunResult {
        val profile = SubagentProfileRegistry.get(spec.profileId)
        val depth = subagentDepthOf(parentEnv.agentRunId) + 1
        // runId 里编码嵌套层数，子 agent 再派子 agent 时能算出真实深度。
        val subagentId = "subagent-$depth-${UUID.randomUUID().toString().take(8)}"
        if (depth > MAX_SUBAGENT_DEPTH) {
            // 不允许无限递归：超出深度直接把结果还给模型，而不是继续建 orchestrator。
            return SubagentRunResult(
                subagentId = subagentId,
                profileId = profile.id,
                taskIndex = taskIndex,
                status = "rejected",
                finalContent = "",
                toolCallSummaries = emptyList(),
                errorMessage = "子任务嵌套层数超过上限（$MAX_SUBAGENT_DEPTH 层），请在当前层级完成任务。"
            )
        }
        return try {
            emitProgress(
                progressReporter,
                progressSequence,
                kind = "subagent_started",
                taskIndex = taskIndex,
                subagentId = subagentId,
                profileId = profile.id,
                summary = "SubAgent #${taskIndex + 1} 开始：${normalizeSubagentProgressText(spec.instruction)}"
            )
            val harnessCatalog = inheritedSubagentCatalog(parentCatalogProvider(), profile)
            val systemMessage = ChatCompletionMessage(
                role = "system",
                content = JsonPrimitive(profile.systemPrompt)
            )
            val userMessage = ChatCompletionMessage(
                role = "user",
                content = JsonPrimitive(spec.instruction)
            )
            val subEnv = DefaultAgentExecutionEnvironment(
                agentRunId = subagentId,
                userMessage = spec.instruction,
                runtimeContextRepository = parentEnv.runtimeContextRepository,
                workspaceDescriptor = parentEnv.workspaceDescriptor,
                resolvedSkills = emptyList(),
                workspaceManager = parentEnv.workspaceManager,
                workspaceMemoryService = parentEnv.workspaceMemoryService,
                conversationMode = parentEnv.conversationMode,
                reasoningEffort = parentEnv.reasoningEffort,
                modelProviderProfileId = parentEnv.modelProviderProfileId,
                terminalEnvironment = parentEnv.terminalEnvironment,
                runControl = NoOpAgentRunControl,
                permissionRequester = parentEnv.permissionRequester,
                longTermMemoryIndex = parentEnv.longTermMemoryIndex
            )
            val silentCallback = ReportingSubagentCallback(
                taskIndex = taskIndex,
                subagentId = subagentId,
                profileId = profile.id,
                progressReporter = progressReporter,
                progressSequence = progressSequence
            )
            val orchestrator = AgentOrchestrator(
                llmClient = llmClient,
                toolRegistry = harnessCatalog,
                toolRouter = toolExecutorProvider(),
                eventAdapter = eventAdapter,
                model = model,
                toolImageContinuationPolicy = toolImageContinuationPolicy,
                ownsToolRouter = false
            )
            val result = orchestrator.run(
                AgentOrchestrator.Input(
                    callback = silentCallback,
                    initialMessages = listOf(systemMessage, userMessage),
                    executionEnv = subEnv,
                    conversationId = null,
                    contextCompactor = contextCompactorFactory?.invoke(),
                )
            )
            when (result) {
                is AgentResult.Success -> {
                    emitProgress(
                        progressReporter,
                        progressSequence,
                        kind = "subagent_completed",
                        taskIndex = taskIndex,
                        subagentId = subagentId,
                        profileId = profile.id,
                        status = "completed",
                        summary = "SubAgent #${taskIndex + 1} 得到结果：${normalizeSubagentProgressText(result.response.content)}"
                    )
                    SubagentRunResult(
                        subagentId = subagentId,
                        profileId = profile.id,
                        taskIndex = taskIndex,
                        status = "completed",
                        finalContent = result.response.content,
                        toolCallSummaries = silentCallback.toolSummaries()
                    )
                }
                is AgentResult.Error -> {
                    emitProgress(
                        progressReporter,
                        progressSequence,
                        kind = "subagent_failed",
                        taskIndex = taskIndex,
                        subagentId = subagentId,
                        profileId = profile.id,
                        status = "failed",
                        summary = "SubAgent #${taskIndex + 1} 失败：${normalizeSubagentProgressText(result.message)}"
                    )
                    SubagentRunResult(
                        subagentId = subagentId,
                        profileId = profile.id,
                        taskIndex = taskIndex,
                        status = "failed",
                        finalContent = "",
                        toolCallSummaries = silentCallback.toolSummaries(),
                        errorMessage = result.message
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            emitProgress(
                progressReporter,
                progressSequence,
                kind = "subagent_failed",
                taskIndex = taskIndex,
                subagentId = subagentId,
                profileId = profile.id,
                status = "failed",
                summary = "SubAgent #${taskIndex + 1} 失败：${normalizeSubagentProgressText(e.message ?: "subagent execution failed")}"
            )
            SubagentRunResult(
                subagentId = subagentId,
                profileId = profile.id,
                taskIndex = taskIndex,
                status = "failed",
                finalContent = "",
                toolCallSummaries = emptyList(),
                errorMessage = e.message ?: "subagent execution failed"
            )
        }
    }

    private suspend fun emitProgress(
        progressReporter: (suspend (SubagentProgressEvent) -> Unit)?,
        progressSequence: AtomicLong,
        kind: String,
        summary: String,
        taskIndex: Int? = null,
        subagentId: String? = null,
        profileId: String? = null,
        status: String = "running",
        toolName: String? = null
    ) {
        if (progressReporter == null || summary.isBlank()) return
        progressReporter(
            SubagentProgressEvent(
                sequence = progressSequence.incrementAndGet(),
                createdAtMs = System.currentTimeMillis(),
                kind = kind,
                summary = summary,
                taskIndex = taskIndex,
                subagentId = subagentId,
                profileId = profileId,
                status = status,
                toolName = toolName
            )
        )
    }

}

/** Role permissions narrow the parent catalog; they never add capabilities or execution budgets. */
internal fun inheritedSubagentCatalog(
    parent: AgentToolCatalog,
    profile: SubagentProfile = SubagentProfileRegistry.general,
): AgentToolCatalog = if (profile.id == "general") parent else SubagentToolCatalogView(parent, profile.id)

/**
 * Callback that swallows subagent streaming output (we don't want subagent
 * intermediate text leaking into the parent's chat stream) while capturing
 * tool-call names so the dispatcher can summarize what the subagent did.
 */
private class ReportingSubagentCallback(
    private val taskIndex: Int,
    private val subagentId: String,
    private val profileId: String,
    private val progressReporter: (suspend (SubagentDispatcher.SubagentProgressEvent) -> Unit)?,
    private val progressSequence: AtomicLong
) : AgentCallback {
    private val tools = mutableListOf<String>()
    private var lastThinkingSummary: String = ""

    fun toolSummaries(): List<String> = tools.toList()

    override suspend fun onThinkingStart() {
        emit(kind = "thinking_started", summary = "SubAgent #${taskIndex + 1} 开始思考")
    }

    override suspend fun onThinkingUpdate(thinking: String) {
        val summary = compactThinking(thinking)
        if (summary.isBlank() || summary == lastThinkingSummary) {
            return
        }
        if (
            lastThinkingSummary.isNotBlank() &&
            summary.startsWith(lastThinkingSummary) &&
            summary.length - lastThinkingSummary.length < 32
        ) {
            return
        }
        lastThinkingSummary = summary
        emit(
            kind = "thinking",
            summary = "SubAgent #${taskIndex + 1} 思考：$summary"
        )
    }

    override suspend fun onToolCallStart(
        toolName: String,
        arguments: kotlinx.serialization.json.JsonObject
    ) {
        tools.add(toolName)
        emit(
            kind = "tool_started",
            summary = "SubAgent #${taskIndex + 1} 调用工具：$toolName",
            toolName = toolName
        )
    }

    override suspend fun onToolCallProgress(
        toolName: String,
        progress: String,
        extras: Map<String, Any?>
    ) {
        val text = progress.trim()
        if (text.isNotEmpty()) {
            emit(
                kind = "tool_progress",
                summary = "SubAgent #${taskIndex + 1} 工具进度：$text",
                toolName = toolName
            )
        }
    }

    override suspend fun onToolCallComplete(toolName: String, result: ToolExecutionResult) {
        val success = result !is ToolExecutionResult.Error
        emit(
            kind = "tool_completed",
            status = if (success) "completed" else "failed",
            summary = "SubAgent #${taskIndex + 1} 工具完成：$toolName",
            toolName = toolName
        )
    }

    override suspend fun onChatMessage(message: String) {
        val text = normalizeSubagentProgressText(message)
        if (text.isNotEmpty()) {
            emit(kind = "message", summary = "SubAgent #${taskIndex + 1} 输出：$text")
        }
    }
    override suspend fun onClarifyRequired(question: String, missingFields: List<String>?) = Unit
    override suspend fun onComplete(result: AgentResult) = Unit
    override suspend fun onError(error: String) = Unit
    override suspend fun onPermissionRequired(missing: List<String>) = Unit

    private suspend fun emit(
        kind: String,
        summary: String,
        status: String = "running",
        toolName: String? = null
    ) {
        if (progressReporter == null || summary.isBlank()) return
        progressReporter(
            SubagentDispatcher.SubagentProgressEvent(
                sequence = progressSequence.incrementAndGet(),
                createdAtMs = System.currentTimeMillis(),
                kind = kind,
                summary = summary,
                taskIndex = taskIndex,
                subagentId = subagentId,
                profileId = profileId,
                status = status,
                toolName = toolName
            )
        )
    }

    private fun compactThinking(text: String): String {
        val lines = text
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val candidate = lines.lastOrNull().orEmpty()
        return normalizeSubagentProgressText(candidate)
    }
}

internal fun normalizeSubagentProgressText(text: String): String = text
    .replace(Regex("\\s+"), " ")
    .trim()
