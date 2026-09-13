package cn.com.omnimind.baselib.llm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialVlmOperationConfigStoreTest {
    @After
    fun clearBundledDefault() {
        OfficialVlmOperationConfigStore.setBundledDefault(null)
    }

    @Test
    fun `bundled default is normalized when no saved config exists`() {
        OfficialVlmOperationConfigStore.setBundledDefault(
            OfficialVlmOperationConfig(
                enabled = true,
                apiBase = " https://omnimind.example/v1/ ",
                model = " gpt-5.6-sol ",
                wireApi = "responses",
            )
        )

        assertEquals(
            OfficialVlmOperationConfig(
                enabled = true,
                apiBase = "https://omnimind.example/v1",
                model = "gpt-5.6-sol",
                wireApi = OpenAiWireApi.RESPONSES,
            ),
            OfficialVlmOperationConfigStore.getConfig(),
        )
    }

    @Test
    fun `legacy persisted upstream keys are detected for removal`() {
        assertEquals(
            true,
            OfficialVlmOperationConfigStore.containsLegacySecretField(
                """{"enabled":true,"apiKey":"must-not-remain"}"""
            )
        )
    }
}
