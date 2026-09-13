package cn.com.omnimind.baselib.llm

object MoonshotProvider {
    const val SOURCE_TYPE = "moonshot"
    const val OFFICIAL_PROFILE_ID = "moonshot-official"
    const val OFFICIAL_PROFILE_NAME = "Kimi"
    const val OFFICIAL_BASE_URL = "https://api.moonshot.cn/v1"

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
            expectedHost = "api.moonshot.cn"
        )
    }
}
