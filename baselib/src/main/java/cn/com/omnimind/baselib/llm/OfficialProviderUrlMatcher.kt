package cn.com.omnimind.baselib.llm

import java.net.URI

internal object OfficialProviderUrlMatcher {
    fun matchesHttpsHostWithOptionalV1(value: String?, expectedHost: String): Boolean {
        val normalized = value
            ?.trim()
            ?.removeSuffix("#")
            ?.trim()
            ?.replace(Regex("/+$"), "")
            .orEmpty()
        if (normalized.isEmpty()) {
            return false
        }
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
        if (uri.scheme?.equals("https", ignoreCase = true) != true) {
            return false
        }
        if (!uri.host.equals(expectedHost, ignoreCase = true)) {
            return false
        }
        val path = uri.path.orEmpty().trimEnd('/')
        return path.isEmpty() || path == "/v1"
    }

    fun matchesHttpsHostWithOptionalV1OrCompatibleMode(
        value: String?,
        expectedHost: String
    ): Boolean {
        val normalized = value
            ?.trim()
            ?.removeSuffix("#")
            ?.trim()
            ?.replace(Regex("/+$"), "")
            .orEmpty()
        if (normalized.isEmpty()) {
            return false
        }
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
        if (uri.scheme?.equals("https", ignoreCase = true) != true) {
            return false
        }
        if (!uri.host.equals(expectedHost, ignoreCase = true)) {
            return false
        }
        val path = uri.path.orEmpty().trimEnd('/')
        return path.isEmpty() || path == "/v1" || path == "/compatible-mode/v1"
    }
}
