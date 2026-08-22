package cn.com.omnimind.baselib.llm

object OpenAiWireApi {
    const val CHAT_COMPLETIONS = "chat_completions"
    const val RESPONSES = "responses"

    fun normalize(value: String?): String {
        return when (value?.trim()?.lowercase()) {
            RESPONSES -> RESPONSES
            "chat-completions",
            "chat/completions",
            "chatcompletions" -> CHAT_COMPLETIONS
            else -> CHAT_COMPLETIONS
        }
    }

    fun isResponses(value: String?): Boolean = normalize(value) == RESPONSES
}
