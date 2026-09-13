package cn.com.omnimind.baselib.llm

object BailianProvider {
    const val SOURCE_TYPE = "bailian"
    const val OFFICIAL_PROFILE_ID = "bailian-official"
    const val OFFICIAL_PROFILE_NAME = "阿里百炼"
    const val OFFICIAL_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

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
        return OfficialProviderUrlMatcher.matchesHttpsHostWithOptionalV1OrCompatibleMode(
            value = value,
            expectedHost = "dashscope.aliyuncs.com"
        )
    }
}
