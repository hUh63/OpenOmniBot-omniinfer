package cn.com.omnimind.bot.agent.tool.handlers

import cn.com.omnimind.bot.agent.*
import cn.com.omnimind.bot.agent.AgentCallback
import cn.com.omnimind.bot.agent.AgentExecutionEnvironment
import cn.com.omnimind.bot.agent.AgentToolExecutionHandle
import cn.com.omnimind.bot.agent.AgentToolRegistry
import cn.com.omnimind.bot.agent.ToolExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class BrowserToolHandler(
    private val helper: SharedHelper,
    private val workspaceManager: cn.com.omnimind.bot.agent.AgentWorkspaceManager
) : ToolHandler {
    override val toolNames: Set<String> = setOf("browser_use", "webfetch")

    override suspend fun execute(
        toolCall: cn.com.omnimind.baselib.llm.AssistantToolCall,
        args: JsonObject,
        runtimeDescriptor: AgentToolRegistry.RuntimeToolDescriptor,
        env: AgentExecutionEnvironment,
        callback: AgentCallback,
        toolHandle: AgentToolExecutionHandle
    ): ToolExecutionResult {
        return executeBrowserUse(args, env, callback, toolHandle)
    }

    override suspend fun dispose() {
        LiveAgentBrowserSessionManager.releaseRunOwnership()
    }

    private suspend fun executeBrowserUse(
        args: JsonObject,
        env: AgentExecutionEnvironment,
        callback: AgentCallback,
        toolHandle: AgentToolExecutionHandle
    ): ToolExecutionResult {
        val toolName = "browser_use"
        return try {
            helper.requireWorkspaceStorageAccess(callback)?.let { return it }
            val request = BrowserUseRequest.fromJson(args)
            val engine = LiveAgentBrowserSessionManager.acquireEngine(
                context = helper.context,
                workspaceManager = workspaceManager,
                agentRunId = env.agentRunId,
                workspace = env.workspaceDescriptor
            )
            toolHandle.bindStopAction {
                engine.requestInterruptCurrentAction(agentRunId = env.agentRunId)
            }
            helper.reportToolProgress(callback, toolName, request.toolTitle, mapOf("summary" to request.toolTitle), toolHandle = toolHandle)
            val outcome = engine.execute(
                request = request,
                agentRunId = env.agentRunId,
                workspace = env.workspaceDescriptor,
            )
            val payload = linkedMapOf<String, Any?>("toolTitle" to request.toolTitle).apply { putAll(outcome.payload) }
            val encoded = helper.encodeLocalizedPayload(payload)
            // 引擎把失败写在 payload 里而不是抛异常（风控拦截、下载失败）。以前一律
            // success=true：模型据此继续自动点击而不是停下来请用户接管，历史/统计里
            // 100% 的浏览器动作都是「成功」，真实失败率被完全掩盖。
            val outcomeFailed = payload["blockedByRiskChallenge"] == true ||
                payload["status"]?.toString()?.equals("failed", ignoreCase = true) == true ||
                payload["errorMessage"]?.toString()?.isNotBlank() == true
            ToolExecutionResult.ContextResult(
                toolName = toolName,
                summaryText = helper.localized(outcome.summaryText),
                previewJson = encoded,
                rawResultJson = encoded,
                success = !outcomeFailed,
                imageDataUrl = outcome.imageDataUrl,
                artifacts = outcome.artifacts,
                workspaceId = env.workspaceDescriptor.id,
                actions = outcome.actions
            )
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            helper.workspacePermissionResult(e, callback)?.let { return it }
            helper.errorResult(toolName, e.message, "浏览器操作失败")
        }
    }
}
