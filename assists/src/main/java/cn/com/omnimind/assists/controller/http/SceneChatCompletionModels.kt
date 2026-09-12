package cn.com.omnimind.assists.controller.http

import cn.com.omnimind.baselib.llm.AssistantToolCall
import cn.com.omnimind.baselib.llm.ModelSceneRegistry
import okhttp3.sse.EventSource

data class SceneChatCompletionResponse(
    val success: Boolean,
    val code: String,
    val message: String,
    val parser: ModelSceneRegistry.ResponseParser,
    val route: String? = null,
    val content: String = "",
    val reasoning: String = "",
    val finishReason: String? = null,
    val toolCalls: List<AssistantToolCall> = emptyList(),
    val rawResponseBody: String? = null
)

data class SceneChatCompletionStreamHandle(
    val eventSource: EventSource,
    val parser: ModelSceneRegistry.ResponseParser,
    val route: String? = null,
    val resolvedModel: String
)
