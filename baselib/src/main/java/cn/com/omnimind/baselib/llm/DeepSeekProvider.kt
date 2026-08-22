package cn.com.omnimind.baselib.llm

object DeepSeekProvider {
    const val OFFICIAL_PROFILE_ID = "deepseek-official"
    const val OFFICIAL_PROFILE_NAME = "DeepSeek"
    const val OFFICIAL_BASE_URL = "https://api.deepseek.com"
    const val PROTOCOL_TYPE = "deepseek"

    fun officialProfile(): ModelProviderProfile {
        return ModelProviderProfile(
            id = OFFICIAL_PROFILE_ID,
            name = OFFICIAL_PROFILE_NAME,
            baseUrl = OFFICIAL_BASE_URL,
            sourceType = PROTOCOL_TYPE,
            protocolType = PROTOCOL_TYPE
        )
    }

    fun isOfficialBaseUrl(value: String?): Boolean {
        return OfficialProviderUrlMatcher.matchesHttpsHostWithOptionalV1(
            value = value,
            expectedHost = "api.deepseek.com"
        )
    }

    fun shouldUseOfficialAdapter(protocolType: String?, apiBase: String?): Boolean {
        return normalizeProtocolType(protocolType) != "anthropic" && isOfficialBaseUrl(apiBase)
    }

    fun normalizeProtocolType(value: String?): String {
        return when (value?.trim()?.lowercase().orEmpty()) {
            PROTOCOL_TYPE -> PROTOCOL_TYPE
            "anthropic" -> "anthropic"
            else -> "openai_compatible"
        }
    }

    fun mapReasoningEffortForOfficialApi(value: String?): String? {
        return when (value?.trim()?.lowercase().orEmpty()) {
            "high", "low", "medium" -> "high"
            "max", "xhigh" -> "max"
            else -> null
        }
    }
}
