package cn.com.omnimind.bot.agent.tool.handlers

import cn.com.omnimind.bot.agent.*
import cn.com.omnimind.bot.agent.AgentCallback
import cn.com.omnimind.bot.agent.AgentExecutionEnvironment
import cn.com.omnimind.bot.agent.AgentToolExecutionHandle
import cn.com.omnimind.bot.agent.AgentToolRegistry
import cn.com.omnimind.bot.agent.ToolExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** memory_search 不传 limit 时的默认条数。 */
private const val DEFAULT_MEMORY_SEARCH_LIMIT = 12
/** 即便模型显式要更多，也不超过这个数（每条命中都带完整正文）。 */
private const val MAX_MEMORY_SEARCH_LIMIT = 40

class MemoryToolHandler(
    private val helper: SharedHelper
) : ToolHandler {
    override val toolNames: Set<String> = setOf(
        "memory_search", "memory_write_daily", "memory_upsert_longterm", "memory_rollup_day"
    )

    override suspend fun execute(
        toolCall: cn.com.omnimind.baselib.llm.AssistantToolCall,
        args: JsonObject,
        runtimeDescriptor: AgentToolRegistry.RuntimeToolDescriptor,
        env: AgentExecutionEnvironment,
        callback: AgentCallback,
        toolHandle: AgentToolExecutionHandle
    ): ToolExecutionResult {
        return executeMemoryTool(toolCall.function.name, args, env, callback)
    }

    private suspend fun executeMemoryTool(
        toolName: String,
        args: JsonObject,
        env: AgentExecutionEnvironment,
        callback: AgentCallback
    ): ToolExecutionResult {
        return try {
            when (toolName) {
                "memory_search" -> {
                    helper.reportToolProgress(callback, toolName, "正在检索 workspace 记忆")
                    val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    require(query.isNotEmpty()) { "query 不能为空" }
                    val requestedLimit = args["limit"]?.jsonPrimitive?.intOrNull
                    require(requestedLimit == null || requestedLimit > 0) {
                        "limit 必须大于 0"
                    }
                    // 默认不能再是 Int.MAX_VALUE：payload 里带每条命中的完整正文，
                    // 而工具输出没有总字节上限 —— 记忆量大时单条 memory_search 结果
                    // 就能把上下文灌爆（反而更快触发压缩）。显式传 limit 时仍照办，
                    // 但同样夹到硬上限。
                    val limit = (requestedLimit ?: DEFAULT_MEMORY_SEARCH_LIMIT)
                        .coerceAtMost(MAX_MEMORY_SEARCH_LIMIT)
                    val result = env.workspaceMemoryService.searchMemory(query, limit)
                    val payload = linkedMapOf<String, Any?>(
                        "query" to result.query,
                        "usedEmbedding" to result.usedEmbedding,
                        "fallbackLexical" to result.fallbackLexical,
                        "count" to result.hits.size,
                        "hits" to result.hits.map { hit ->
                            mapOf(
                                "id" to hit.id,
                                "slug" to hit.slug,
                                "text" to hit.text,
                                "source" to hit.source,
                                "date" to hit.date,
                                "score" to hit.score
                            )
                        }
                    )
                    val payloadJson = helper.encodeLocalizedPayload(payload)
                    ToolExecutionResult.ContextResult(
                        toolName = toolName,
                        summaryText = helper.localized(when {
                            result.hits.isEmpty() -> "未命中相关记忆。"
                            result.fallbackLexical -> "命中 ${result.hits.size} 条记忆（词法检索）。"
                            else -> "命中 ${result.hits.size} 条记忆。"
                        }),
                        previewJson = payloadJson, rawResultJson = payloadJson, success = true
                    )
                }
                "memory_write_daily" -> {
                    helper.reportToolProgress(callback, toolName, "正在写入当日记忆")
                    val text = args["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    require(text.isNotEmpty()) { "text 不能为空" }
                    env.workspaceMemoryService.appendDailyMemory(text)
                    val payload = mapOf(
                        "inserted" to true,
                        "summary" to "已写入当日记忆"
                    )
                    val payloadJson = helper.encodeLocalizedPayload(payload)
                    ToolExecutionResult.ContextResult(
                        toolName = toolName,
                        summaryText = helper.localized("已写入当日短期记忆。"),
                        previewJson = payloadJson, rawResultJson = payloadJson, success = true
                    )
                }
                "memory_upsert_longterm" -> {
                    helper.reportToolProgress(callback, toolName, "正在沉淀长期记忆")
                    val text = args["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    require(text.isNotEmpty()) { "text 不能为空" }
                    env.workspaceMemoryService.appendLongTermMemory(text)
                    val payload = mapOf("inserted" to true, "summary" to "已写入长期记忆")
                    val payloadJson = helper.encodeLocalizedPayload(payload)
                    ToolExecutionResult.ContextResult(
                        toolName = toolName,
                        summaryText = helper.localized("已沉淀一条长期记忆。"),
                        previewJson = payloadJson, rawResultJson = payloadJson, success = true
                    )
                }
                "memory_rollup_day" -> {
                    helper.reportToolProgress(callback, toolName, "正在整理当日记忆")
                    val dateRaw = args["date"]?.jsonPrimitive?.contentOrNull?.trim()
                    val date = dateRaw?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) } ?: LocalDate.now()
                    val payload = env.workspaceMemoryService.rollupDay(date)
                    val payloadJson = helper.encodeLocalizedPayload(payload)
                    ToolExecutionResult.ContextResult(
                        toolName = toolName,
                        summaryText = helper.localized(payload["summary"]?.toString().orEmpty().ifBlank { "记忆整理完成" }),
                        previewJson = payloadJson, rawResultJson = payloadJson, success = payload["success"] != false
                    )
                }
                else -> ToolExecutionResult.Error(toolName, "Unknown memory tool")
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ToolExecutionResult.Error(toolName, helper.localized(e.message ?: "memory tool failed")) }
    }
}
