package cn.com.omnimind.baselib.llm

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧缓存兼容：历史缓存 JSON 缺少后加的字段时，Gson 会把 Kotlin 非空参数填成 null，
 * 因此读取路径必须先补齐默认值再反序列化，否则 copy()/界面读取会失败。
 */
class ModelProviderLegacyCacheCompatTest {

    private fun parse(raw: String): ProviderModelOption =
        Gson().fromJson(
            ModelProviderConfigStore.repairLegacyCachedModel(JsonParser.parseString(raw)),
            ProviderModelOption::class.java,
        )

    @Test
    fun `missing optional fields fall back to defaults`() {
        val model = parse("""{"id":"legacy"}""")

        assertEquals("legacy", model.id)
        assertEquals("legacy", model.displayName)
        assertTrue(model.inputModalities.isEmpty())
        assertTrue(model.outputModalities.isEmpty())
        assertTrue(model.supportedReasoningLevels.isEmpty())
    }

    @Test
    fun `explicit nulls are treated as missing`() {
        val model = parse(
            """{"id":"legacy","displayName":null,"inputModalities":null,"outputModalities":null,"supportedReasoningLevels":null}""",
        )

        assertEquals("legacy", model.displayName)
        assertTrue(model.inputModalities.isEmpty())
        assertTrue(model.outputModalities.isEmpty())
        assertTrue(model.supportedReasoningLevels.isEmpty())
    }

    @Test
    fun `blank display name falls back to id`() {
        assertEquals("legacy", parse("""{"id":"legacy","displayName":"   "}""").displayName)
    }

    @Test
    fun `declared capabilities are preserved`() {
        val model = parse(
            """{"id":"m1","displayName":"Model 1","inputModalities":["text","image"],""" +
                """"outputModalities":["text"],"supportedReasoningLevels":["low","high"]}""",
        )

        assertEquals("Model 1", model.displayName)
        assertEquals(listOf("text", "image"), model.inputModalities)
        assertEquals(listOf("text"), model.outputModalities)
        assertEquals(listOf("low", "high"), model.supportedReasoningLevels)
    }

    @Test
    fun `repaired model can be copied`() {
        val repaired = parse("""{"id":"legacy"}""").copy(displayName = "renamed")

        assertEquals("renamed", repaired.displayName)
        assertTrue(repaired.inputModalities.isEmpty())
    }
}
