package cn.com.omnimind.bot.agent

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserUseSupportTest {
    @Test
    fun normalizeKeywordsSupportsWhitespaceSeparatedString() {
        val keywords = BrowserUseSupport.normalizeKeywords(JsonPrimitive("session auth token"))
        assertEquals(listOf("session", "auth", "token"), keywords)
    }

    @Test
    fun normalizeKeywordsSupportsArrayInput() {
        val keywords = BrowserUseSupport.normalizeKeywords(
            buildJsonArray {
                add(JsonPrimitive("session"))
                add(JsonPrimitive("auth_token"))
            }
        )
        assertEquals(listOf("session", "auth_token"), keywords)
    }

    @Test
    fun filterCookieNamesSupportsFuzzyAndExactMatching() {
        val names = listOf("session_id", "auth_token", "csrftoken")

        val fuzzy = BrowserUseSupport.filterCookieNames(
            names = names,
            keywords = listOf("session", "id"),
            fuzzy = true
        )
        assertEquals(listOf("session_id"), fuzzy)

        val exact = BrowserUseSupport.filterCookieNames(
            names = names,
            keywords = listOf("AUTH_TOKEN"),
            fuzzy = false
        )
        assertEquals(listOf("auth_token"), exact)
    }

    @Test
    fun sanitizeCookieEnvNameReplacesUnsupportedCharacters() {
        val envName = BrowserUseSupport.sanitizeCookieEnvName("cf.clearance-token")
        assertEquals("COOKIE_CF_CLEARANCE_TOKEN", envName)
    }
}
