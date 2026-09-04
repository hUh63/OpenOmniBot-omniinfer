package cn.com.omnimind.baselib.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenAIResponsesRequest(
    val model: String,
    val input: List<JsonElement> = emptyList(),
    val instructions: String? = null,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<JsonElement> = emptyList(),
    @SerialName("tool_choice")
    val toolChoice: JsonElement? = null,
    @SerialName("parallel_tool_calls")
    val parallelToolCalls: Boolean? = null,
    val reasoning: JsonElement? = null,
    @SerialName("prompt_cache_key")
    val promptCacheKey: String? = null
)
