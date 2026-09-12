package cn.com.omnimind.baselib.llm

import cn.com.omnimind.baselib.util.OmniLog
import com.google.gson.Gson
import com.tencent.mmkv.MMKV

object SceneOperationConfigStore {
    private const val TAG = "SceneOperationConfigStore"
    private const val KEY_SCENE_OPERATION_CONFIG = "scene_operation_config_v1"

    const val SCENE_ID = "scene.vlm.operation.primary"

    private val gson = Gson()
    private val defaultConfig = SceneOperationConfig()

    fun getConfig(): SceneOperationConfig {
        val raw = MMKV.defaultMMKV()
            ?.decodeString(KEY_SCENE_OPERATION_CONFIG)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return defaultConfig
        return parse(raw) ?: defaultConfig
    }

    fun saveConfig(config: SceneOperationConfig): SceneOperationConfig {
        MMKV.defaultMMKV()?.encode(KEY_SCENE_OPERATION_CONFIG, gson.toJson(config))
        return config
    }

    internal fun parse(raw: String): SceneOperationConfig? {
        return runCatching {
            gson.fromJson(raw, SceneOperationConfig::class.java)
        }.onFailure {
            OmniLog.w(TAG, "parse operation config failed: ${it.message}")
        }.getOrNull()
    }
}
