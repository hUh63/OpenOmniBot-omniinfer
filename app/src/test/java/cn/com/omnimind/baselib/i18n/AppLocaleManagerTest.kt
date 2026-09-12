package cn.com.omnimind.baselib.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLocaleManagerTest {
    @Test
    fun resolvePromptLocaleRespectsExplicitModeAndSystemFallback() {
        assertEquals(
            PromptLocale.EN_US,
            AppLocaleManager.resolvePromptLocale(AppLanguageMode.EN, Locale.SIMPLIFIED_CHINESE)
        )
        assertEquals(
            PromptLocale.ZH_CN,
            AppLocaleManager.resolvePromptLocale(AppLanguageMode.ZH_HANS, Locale.US)
        )
        assertEquals(
            PromptLocale.EN_US,
            AppLocaleManager.resolvePromptLocale(AppLanguageMode.SYSTEM, Locale.US)
        )
        assertEquals(
            PromptLocale.ZH_CN,
            AppLocaleManager.resolvePromptLocale(AppLanguageMode.SYSTEM, Locale.SIMPLIFIED_CHINESE)
        )
    }
}
