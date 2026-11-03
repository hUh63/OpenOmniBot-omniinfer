package cn.com.omnimind.bot.agent.personal

import android.content.Context
import cn.com.omnimind.baselib.i18n.AppLocaleManager
import cn.com.omnimind.baselib.llm.AssistantToolCall
import cn.com.omnimind.bot.agent.*
import cn.com.omnimind.bot.agent.tool.AgentCapabilityModule
import cn.com.omnimind.bot.agent.tool.AgentCapabilityToolDefinition
import cn.com.omnimind.bot.agent.tool.handlers.ToolHandler
import kotlinx.serialization.json.*

internal class PersonalContextCapabilityModule(context: Context) : AgentCapabilityModule {
    override val toolDefinitions = PersonalContextTools.definitions(AppLocaleManager.resolvePromptLocale(context)).map {
        val function = it.getValue("function").jsonObject
        AgentCapabilityToolDefinition(function.getValue("name").jsonPrimitive.content, function.getValue("displayName").jsonPrimitive.content,
            function.getValue("description").jsonPrimitive.content, function.getValue("parameters").jsonObject, function.getValue("toolType").jsonPrimitive.content)
    }
    override val handlers: List<ToolHandler> = listOf(object : ToolHandler {
        private val service = PersonalContextService(context.applicationContext)
        override val toolNames = PersonalContextTools.names
        override suspend fun execute(toolCall: AssistantToolCall, args: JsonObject, runtimeDescriptor: AgentToolRegistry.RuntimeToolDescriptor,
            env: AgentExecutionEnvironment, callback: AgentCallback, toolHandle: AgentToolExecutionHandle): ToolExecutionResult {
            val result = service.execute(toolCall.function.name, args)
            val json = result.contextJson().toString()
            return ToolExecutionResult.ContextResult(toolName = toolCall.function.name,
                summaryText = result["message"]?.toString() ?: runtimeDescriptor.displayName,
                previewJson = json, rawResultJson = json, success = result["success"] != false)
        }
    })
}
