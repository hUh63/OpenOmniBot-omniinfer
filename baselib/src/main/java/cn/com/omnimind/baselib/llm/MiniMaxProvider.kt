package cn.com.omnimind.baselib.llm

object MiniMaxProvider {
    const val SOURCE_TYPE = "minimax"
    const val OFFICIAL_PROFILE_ID = "minimax-official"
    const val OFFICIAL_PROFILE_NAME = "MiniMax"
    const val OFFICIAL_BASE_URL = "https://api.minimaxi.com/v1"

    fun officialProfile(): ModelProviderProfile {
        return ModelProviderProfile(
            id = OFFICIAL_PROFILE_ID,
            name = OFFICIAL_PROFILE_NAME,
            baseUrl = OFFICIAL_BASE_URL,
            sourceType = SOURCE_TYPE,
            protocolType = "openai_compatible",
            wireApi = OpenAiWireApi.CHAT_COMPLETIONS
        )
    }

    fun isOfficialBaseUrl(value: String?): Boolean {
        return OfficialProviderUrlMatcher.matchesHttpsHostWithOptionalV1(
            value = value,
            expectedHost = "api.minimaxi.com"
        )
    }
}
