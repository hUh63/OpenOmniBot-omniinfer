package cn.com.omnimind.baselib.config

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开源版配置加载器：仅保留本地内置场景配置，不进行任何远程拉取或缓存回填。
 */
object RemoteConfigLoader {
    private const val TAG = "RemoteConfigLoader"

    suspend fun loadIfNeeded(context: Context) {
        OmniLog.d(TAG, "OSS build: skip remote config loading, use builtin scene config only")
    }

    /**
     * @deprecated 开源版不支持远程配置加载
     */
    @Deprecated("OSS build does not support remote config loading")
    fun loadConfigAsync(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch {
            OmniLog.d(TAG, "OSS build: loadConfigAsync ignored")
        }
    }

    suspend fun loadConfig(context: Context) {
        OmniLog.d(TAG, "OSS build: loadConfig ignored")
    }
}
