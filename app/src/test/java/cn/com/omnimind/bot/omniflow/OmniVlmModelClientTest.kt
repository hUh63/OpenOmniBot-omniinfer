package cn.com.omnimind.bot.omniflow

import cn.com.omnimind.baselib.llm.AssistantToolCall
import cn.com.omnimind.baselib.llm.AssistantToolCallFunction
import cn.com.omnimind.baselib.llm.ChatCompletionFunction
import cn.com.omnimind.baselib.llm.ChatCompletionMessage
import cn.com.omnimind.baselib.llm.ChatCompletionRequest
import cn.com.omnimind.baselib.llm.ChatCompletionTool
import cn.com.omnimind.baselib.llm.ChatCompletionTurn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class OmniVlmModelClientTest {
    @Test
    fun `qwen normalized point is adapted to the current display pixels`() {
        val request = request(
            tool = "click",
            properties = buildJsonObject {
                put("x", buildJsonObject { put("maximum", 1080) })
                put("y", buildJsonObject { put("maximum", 2376) })
            },
        )
        val turn = turn("{\"x\":874,\"y\":850}")

        val adapted = turn.adaptQwenVlmCoordinates(request)

        assertEquals("{\"x\":943.92,\"y\":2019.6}", adapted.message.toolCalls!![0].function.arguments)
    }

    @Test
    fun `qwen normalized coordinate pair is adapted without leaving array syntax`() {
        val request = request(
            tool = "click",
            properties = buildJsonObject {
                put("x", buildJsonObject { put("maximum", 1080) })
                put("y", buildJsonObject { put("maximum", 2376) })
            },
        )
        val turn = turn("{\"x\":[874,850],\"y\":[850]}")

        val adapted = turn.adaptQwenVlmCoordinates(request)

        assertEquals("{\"x\":943.92,\"y\":2019.6}", adapted.message.toolCalls!![0].function.arguments)
    }

    private fun request(tool: String, properties: kotlinx.serialization.json.JsonObject) =
        ChatCompletionRequest(
            model = "Qwen3-VL-235B-A22B-Instruct",
            messages = emptyList(),
            tools = listOf(
                ChatCompletionTool(
                    function = ChatCompletionFunction(
                        name = tool,
                        parameters = buildJsonObject {
                            put("properties", properties)
                        },
                    ),
                ),
            ),
        )

    private fun turn(arguments: String) = ChatCompletionTurn(
        message = ChatCompletionMessage(
            role = "assistant",
            toolCalls = listOf(
                AssistantToolCall(
                    id = "call-1",
                    function = AssistantToolCallFunction(
                        name = "click",
                        arguments = arguments,
                    ),
                ),
            ),
        ),
        resolvedModel = "Qwen3-VL-235B-A22B-Instruct",
    )
}
