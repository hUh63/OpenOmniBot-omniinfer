package cn.com.omnimind.bot.vlm

import cn.com.omnimind.baselib.llm.OfficialVlmOperationConfig
import cn.com.omnimind.baselib.llm.OpenAiWireApi

object BundledVlmOperationConfig {
    internal fun create(
        apiBase: String,
        model: String,
    ): OfficialVlmOperationConfig? {
        return OfficialVlmOperationConfig(
            enabled = true,
            apiBase = apiBase,
            model = model,
            wireApi = OpenAiWireApi.CHAT_COMPLETIONS,
        ).takeIf(OfficialVlmOperationConfig::isConfigured)
    }
}
