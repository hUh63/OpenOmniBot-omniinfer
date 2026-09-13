package cn.com.omnimind.baselib.llm

object MimoProvider {
    const val SOURCE_TYPE = "mimo"
    const val OFFICIAL_PROFILE_ID = "mimo-official"
    const val OFFICIAL_PROFILE_NAME = "Mimo"
    const val OFFICIAL_BASE_URL = "https://api.xiaomimimo.com/v1"

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
            expectedHost = "api.xiaomimimo.com"
        )
    }
}
