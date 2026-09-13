package cn.com.omnimind.baselib.llm

import cn.com.omnimind.baselib.util.OmniLog
import com.google.gson.Gson
import com.tencent.mmkv.MMKV

object OfficialVlmOperationConfigStore {
    private const val TAG = "OfficialVlmOperationConfigStore"
    private const val KEY_OFFICIAL_VLM_OPERATION_CONFIG =
        "official_vlm_operation_config_v1"

    private val gson = Gson()
    private val defaultConfig = OfficialVlmOperationConfig()

    @Volatile
    private var bundledDefault: OfficialVlmOperationConfig? = null

    fun getConfig(): OfficialVlmOperationConfig {
        val raw = runCatching {
            MMKV.defaultMMKV().decodeString(KEY_OFFICIAL_VLM_OPERATION_CONFIG)
        }.getOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val saved = raw?.let(::parse)
        if (saved != null && containsLegacySecretField(raw)) {
            saveConfig(saved)
        }
        return saved ?: bundledDefault ?: defaultConfig
    }

    fun saveConfig(config: OfficialVlmOperationConfig): OfficialVlmOperationConfig {
        val normalized = normalize(config)
        MMKV.defaultMMKV()?.encode(
            KEY_OFFICIAL_VLM_OPERATION_CONFIG,
            gson.toJson(normalized)
        )
        return normalized
    }

    fun setBundledDefault(config: OfficialVlmOperationConfig?) {
        bundledDefault = config
            ?.let(::normalize)
            ?.takeIf(OfficialVlmOperationConfig::isConfigured)
    }

    fun normalize(config: OfficialVlmOperationConfig): OfficialVlmOperationConfig {
        return OfficialVlmOperationConfig(
            enabled = config.enabled,
            apiBase = config.apiBase.trim().trimEnd('/'),
            model = config.model.trim(),
            wireApi = OpenAiWireApi.normalize(config.wireApi)
        )
    }

    internal fun parse(raw: String): OfficialVlmOperationConfig? {
        return runCatching {
            gson.fromJson(raw, OfficialVlmOperationConfig::class.java)
        }.onFailure {
            OmniLog.w(TAG, "parse official VLM config failed: ${it.message}")
        }.getOrNull()?.let(::normalize)
    }

    internal fun containsLegacySecretField(raw: String): Boolean {
        return raw.contains("\"apiKey\"") || raw.contains("\"api_key\"")
    }
}
