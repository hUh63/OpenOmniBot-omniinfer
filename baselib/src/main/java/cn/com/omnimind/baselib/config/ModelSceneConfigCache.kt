package cn.com.omnimind.baselib.config

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import cn.com.omnimind.baselib.util.OmniLog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type
import kotlin.math.absoluteValue

/**
 * ModelScenes 配置缓存管理器
 * 
 * 职责：
 * 1. 保存/读取配置到私有存储（context.filesDir）
 * 2. 元数据和配置内容存储在同一个 JSON 文件中
 * 3. 检查是否需要加载（版本更新 或 30分钟间隔）
 * 
 * 缓存文件格式：
 * ```json
 * {
 *   "metadata": {
 *     "version_code": 101,
 *     "load_time": 1736668200000
 *   },
 *   "scenes": {
 *     "scene.dispatch.model": {
 *       "model": "qwen3.5-plus-2026-02-15",
 *       "prompt": null,
 *       "description": "..."
 *     }
 *   }
 * }
 * ```
 */
object ModelSceneConfigCache {
    private const val TAG = "ModelSceneConfigCache"
    private const val CACHE_FILE_NAME = "model_scenes_cache.json"
    private const val LOAD_INTERVAL_MS = 30 * 60 * 1000L // 30分钟

    /**
     * 自定义字符串反序列化器，保留转义字符如 \t, \n 等作为字面量
     * 
     * 问题：Gson 在解析 JSON 时，会将 "\t" 解析为实际的制表符字符
     * 解决方案：在反序列化时，检测到这些转义字符后，将其转换回字面量形式（如 "\\t"）
     * 
     * 注意：此方法会影响所有字符串的反序列化，将实际的转义字符转换为字面量
     */
    private class StringLiteralDeserializer : JsonDeserializer<String> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): String {
            if (json == null || !json.isJsonPrimitive) {
                return ""
            }
            val jsonString = json.asString
            
            // 反序列化时，将 Gson 解析后的转义字符转换回字面量形式
            // 这样 "\t" 会被保留为字符串 "\\t"（两个字符：反斜杠和t）
            return jsonString
                .replace("\t", "\\t")   // 将制表符转换回 \t 字面量
                .replace("\n", "\\n")   // 将换行符转换回 \n 字面量

        }
    }

    /**
     * 配置 Gson 以保留转义字符
     * 使用自定义的字符串反序列化器，在解析时将转义字符（如 \t）保留为字面量形式
     */
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(String::class.java, StringLiteralDeserializer())
        .create()

    /**
     * 缓存文件的数据结构
     */
    data class CacheData(
        val metadata: Metadata,
        val scenes: Map<String, Map<String, Any?>>
    )

    data class Metadata(
        val version_code: Int,
        val load_time: Long
    )

    /**
     * 获取缓存文件
     */
    private fun getCacheFile(context: Context): File {
        return File(context.filesDir, CACHE_FILE_NAME)
    }

    /**
     * 获取当前应用版本号
     */
    private fun getVersionCode(context: Context): Int {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "获取版本号失败", e)
            -1
        }
    }

    /**
     * 保存配置到缓存（包含元数据）
     * 
     * @param context Context
     * @param scenes 场景配置 Map
     */
    fun saveConfig(context: Context, scenes: Map<String, Map<String, Any?>>) {
        try {
            val cacheData = CacheData(
                metadata = Metadata(
                    version_code = getVersionCode(context),
                    load_time = System.currentTimeMillis()
                ),
                scenes = scenes
            )

            val json = gson.toJson(cacheData)
            val cacheFile = getCacheFile(context)
            cacheFile.writeText(json)

            OmniLog.i(TAG, "✅ 配置缓存保存成功: ${cacheFile.absolutePath}")
            OmniLog.d(TAG, "缓存包含 ${scenes.size} 个场景，版本号=${cacheData.metadata.version_code}")
        } catch (e: Exception) {
            OmniLog.e(TAG, "❌ 保存配置缓存失败", e)
        }
    }

    /**
     * 读取缓存配置（只返回 scenes）
     * 
     * @param context Context
     * @return 场景配置 Map，如果缓存不存在或读取失败返回 null
     */
    fun loadConfig(context: Context): Map<String, Map<String, Any?>>? {
        return try {
            val cacheFile = getCacheFile(context)
            if (!cacheFile.exists()) {
                OmniLog.d(TAG, "缓存文件不存在")
                return null
            }

            val json = cacheFile.readText()
            val cacheData = gson.fromJson<CacheData>(
                json,
                object : TypeToken<CacheData>() {}.type
            )

            if (cacheData?.scenes == null) {
                OmniLog.w(TAG, "缓存文件格式错误，scenes 为空")
                return null
            }

            OmniLog.i(TAG, "✅ 从缓存加载配置成功，包含 ${cacheData.scenes.size} 个场景")
            OmniLog.d(TAG, "缓存元数据: version_code=${cacheData.metadata.version_code}, load_time=${cacheData.metadata.load_time}")
            
            cacheData.scenes
        } catch (e: Exception) {
            OmniLog.e(TAG, "❌ 读取配置缓存失败", e)
            null
        }
    }

    /**
     * 检查是否需要加载配置
     * 
     * 触发条件（满足任一即可）：
     * 1. 版本号变化（App 更新）
     * 2. 距离上次加载时间 >= 30 分钟
     * 3. 缓存文件不存在
     * 
     * @param context Context
     * @return true 表示需要加载，false 表示不需要
     */
    fun shouldLoad(context: Context): Boolean {
        try {
            val cacheFile = getCacheFile(context)
            
            // 条件1: 缓存文件不存在
            if (!cacheFile.exists()) {
                OmniLog.i(TAG, "✅ 需要加载：缓存文件不存在")
                return true
            }

            // 读取缓存元数据
            val json = cacheFile.readText()
            val cacheData = gson.fromJson<CacheData>(
                json,
                object : TypeToken<CacheData>() {}.type
            )

            if (cacheData?.metadata == null) {
                OmniLog.w(TAG, "✅ 需要加载：缓存元数据缺失")
                return true
            }

            val currentVersionCode = getVersionCode(context)
            val cachedVersionCode = cacheData.metadata.version_code
            val currentTime = System.currentTimeMillis()
            val cachedLoadTime = cacheData.metadata.load_time
            val timeDiff = (currentTime - cachedLoadTime).absoluteValue

            // 条件2: 版本号变化
            if (currentVersionCode != cachedVersionCode) {
                OmniLog.i(TAG, "✅ 需要加载：版本号变化 ($cachedVersionCode -> $currentVersionCode)")
                return true
            }

            // 条件3: 时间间隔 >= 30分钟
            if (timeDiff >= LOAD_INTERVAL_MS) {
                OmniLog.i(TAG, "✅ 需要加载：距离上次加载已超过 ${timeDiff / 1000 / 60} 分钟")
                return true
            }

            // 不需要加载
            val remainingMinutes = (LOAD_INTERVAL_MS - timeDiff) / 1000 / 60
            OmniLog.d(TAG, "⏸️  跳过加载：距离上次加载 ${timeDiff / 1000 / 60} 分钟，还需等待 $remainingMinutes 分钟")
            return false

        } catch (e: Exception) {
            OmniLog.e(TAG, "❌ 检查加载条件时出错，默认需要加载", e)
            return true
        }
    }

    /**
     * 清空缓存文件
     * 
     * @param context Context
     */
    fun clearCache(context: Context) {
        try {
            val cacheFile = getCacheFile(context)
            if (cacheFile.exists()) {
                cacheFile.delete()
                OmniLog.i(TAG, "✅ 缓存文件已清除")
            } else {
                OmniLog.d(TAG, "缓存文件不存在，无需清除")
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "❌ 清除缓存失败", e)
        }
    }
}
