package cn.com.omnimind.bot.vlm

import cn.com.omnimind.baselib.llm.OpenAiWireApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BundledVlmOperationConfigTest {
    @Test
    fun `configured build creates Chat Completions default`() {
        val config = BundledVlmOperationConfig.create(
            apiBase = "https://omnimind.example/v1",
            model = "gpt-5.6-sol",
        )

        assertEquals(OpenAiWireApi.CHAT_COMPLETIONS, config?.wireApi)
        assertEquals("https://omnimind.example/v1", config?.apiBase)
    }

    @Test
    fun `incomplete build does not create default`() {
        assertNull(
            BundledVlmOperationConfig.create(
                apiBase = "",
                model = "gpt-5.6-sol",
            )
        )
    }
}
