package cn.com.omnimind.baselib.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfficialProviderRegistryTest {

    @Test
    fun findByBaseUrl_matchesOfficialHostsWithOptionalV1() {
        assertEquals(
            "deepseek",
            OfficialProviderRegistry.findByBaseUrl("https://api.deepseek.com/v1")?.key
        )
        assertEquals(
            "mimo",
            OfficialProviderRegistry.findByBaseUrl("https://api.xiaomimimo.com")?.key
        )
        assertEquals(
            "moonshot",
            OfficialProviderRegistry.findByBaseUrl("https://api.moonshot.cn/v1/")?.key
        )
        assertEquals(
            "minimax",
            OfficialProviderRegistry.findByBaseUrl("https://api.minimaxi.com/v1#")?.key
        )
        assertEquals(
            "bailian",
            OfficialProviderRegistry.findByBaseUrl(
                "https://dashscope.aliyuncs.com/compatible-mode/v1"
            )?.key
        )
    }

    @Test
    fun normalizeSourceType_prefersKnownOfficialProvidersAndFallsBackToCustom() {
        assertEquals(
            "deepseek",
            OfficialProviderRegistry.normalizeSourceType(
                sourceType = "deepseek",
                profileId = "profile-1",
                baseUrl = "https://example.com/v1"
            )
        )
        assertEquals(
            "moonshot",
            OfficialProviderRegistry.normalizeSourceType(
                sourceType = null,
                profileId = "moonshot-official",
                baseUrl = ""
            )
        )
        assertEquals(
            "minimax",
            OfficialProviderRegistry.normalizeSourceType(
                sourceType = null,
                profileId = "profile-2",
                baseUrl = "https://api.minimaxi.com/v1"
            )
        )
        assertEquals(
            "bailian",
            OfficialProviderRegistry.normalizeSourceType(
                sourceType = null,
                profileId = "bailian-official",
                baseUrl = ""
            )
        )
        assertEquals(
            "custom",
            OfficialProviderRegistry.normalizeSourceType(
                sourceType = "retired-local",
                profileId = "profile-3",
                baseUrl = ""
            )
        )
        assertEquals(
            "custom",
            OfficialProviderRegistry.normalizeSourceType(
                sourceType = null,
                profileId = "profile-4",
                baseUrl = "https://example.com/v1"
            )
        )
        assertNotNull(OfficialProviderRegistry.findByKey("mimo"))
    }
}
