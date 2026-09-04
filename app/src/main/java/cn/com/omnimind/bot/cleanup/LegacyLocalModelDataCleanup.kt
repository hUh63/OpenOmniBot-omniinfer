package cn.com.omnimind.bot.cleanup

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.agent.AgentWorkspaceManager
import com.tencent.mmkv.MMKV
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Removes app-private data left by the retired on-device model runtimes.
 *
 * Configuration is cleaned synchronously so no startup component can import a
 * retired local provider. Large model directories are deleted on an IO thread.
 * The completion marker is written only after both phases succeed, making the
 * migration safe to retry after a process kill or storage error.
 */
object LegacyLocalModelDataCleanup {
    private const val TAG = "LegacyLocalModelCleanup"
    private const val WORK_NAME = "legacy_local_model_cleanup_v1"
    private const val PREFS_NAME = "legacy_local_model_cleanup"
    private const val KEY_CLEANUP_V1_COMPLETE = "cleanup_v1_complete"
    private const val LOCAL_MMKV_ID = "omniinfer_config"
    private const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"
    private const val FLUTTER_KEY_PREFIX = "flutter."
    private const val CURRENT_LOCAL_PROFILE_ID = "omniinfer-local"
    private const val LEGACY_LOCAL_PROFILE_ID = "mnn-local"

    fun start(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CLEANUP_V1_COMPLETE, false)) {
            return
        }

        cleanupConfiguration(appContext)
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LegacyLocalModelCleanupWorker>().build()
        )
    }

    internal fun cleanupConfiguration(context: Context): Boolean {
        return runCatching {
            val defaultMmkv = requireNotNull(MMKV.defaultMMKV()) {
                "default MMKV is unavailable"
            }
            val editingProfileId = defaultMmkv
                .decodeString("model_provider_editing_profile_id")
                ?.trim()
                .orEmpty()
            val editingLocalProfile = isLocalProfileId(editingProfileId)

            defaultMmkv.removeValuesForKeys(
                arrayOf(
                    "mnn_local_provider_port",
                    "mnn_local_provider_api_key",
                    "mnn_local_provider_ready"
                )
            )
            rewriteMmkvJson(
                defaultMmkv,
                "model_provider_profiles_v1",
                ::sanitizeProviderProfilesJson
            )
            rewriteMmkvJson(
                defaultMmkv,
                "scene_model_binding_map_v1",
                ::sanitizeSceneBindingsJson
            )
            if (editingLocalProfile) {
                defaultMmkv.removeValuesForKeys(
                    arrayOf(
                        "model_provider_editing_profile_id",
                        "model_provider_openai_base_url",
                        "model_provider_openai_api_key"
                    )
                )
            }

            MMKV.mmkvWithID(LOCAL_MMKV_ID).clearAll()
            context.getSystemService(NotificationManager::class.java)?.let { manager ->
                manager.cancel(8889)
                manager.deleteNotificationChannel("ModelDownloadChannel")
            }

            val flutterPrefs = context.getSharedPreferences(
                FLUTTER_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val editor = flutterPrefs.edit()
            listOf(
                "manual_provider_model_ids_v2",
                "hidden_chat_provider_model_ids_v1",
                "cached_provider_models_with_base_v2"
            ).forEach { key ->
                rewriteFlutterJson(
                    flutterPrefs.getString(flutterKey(key), null),
                    key,
                    editor,
                    ::sanitizeProfileBucketsJson
                )
            }
            rewriteFlutterJson(
                flutterPrefs.getString(flutterKey("conversation_model_overrides_v1"), null),
                "conversation_model_overrides_v1",
                editor,
                ::sanitizeConversationOverridesJson
            )
            if (editingLocalProfile) {
                editor.remove(flutterKey("manual_provider_model_ids_v1"))
                editor.remove(flutterKey("cached_provider_models_with_base_v1"))
            }
            check(editor.commit()) { "failed to persist Flutter preference cleanup" }

            true
        }.onFailure {
            OmniLog.w(TAG, "configuration cleanup failed: ${it.message}")
        }.getOrDefault(false)
    }

    internal fun cleanupFiles(context: Context): Boolean {
        val targets = linkedSetOf(
            File(context.filesDir, ".mnnmodels"),
            File(context.filesDir, "omniinfer"),
            File(context.filesDir, "tmps"),
            File(context.filesDir, "local_temps"),
            File(context.filesDir, "builtin_temps"),
            File(context.applicationInfo.dataDir, "workspace/.omnibot/models"),
            File(context.filesDir, "workspace/.omnibot/models"),
            File(AgentWorkspaceManager.LEGACY_EXTERNAL_ROOT_PATH, ".omnibot/models"),
            File(context.cacheDir, "omniinfer")
        )
        context.getExternalFilesDirs(null)
            .filterNotNull()
            .forEach { root ->
                targets += File(root, "omniinfer-llama")
                targets += File(root, "OmniInfer-llama")
                targets += File(root, ".mnnmodels")
            }
        context.externalCacheDirs
            .filterNotNull()
            .forEach { root ->
                targets += File(root, "omniinfer")
            }

        var success = true
        targets.forEach { target ->
            val deleted = runCatching { deleteIfPresent(target) }
                .onFailure {
                    OmniLog.w(TAG, "failed to delete ${target.absolutePath}: ${it.message}")
                }
                .getOrDefault(false)
            if (!deleted) {
                success = false
            }
        }
        return success
    }

    private fun deleteIfPresent(file: File): Boolean {
        if (!file.exists()) {
            return true
        }
        return file.deleteRecursively() && !file.exists()
    }

    private fun rewriteMmkvJson(
        mmkv: MMKV,
        key: String,
        sanitizer: (String) -> String
    ) {
        val raw = mmkv.decodeString(key)?.takeIf { it.isNotBlank() } ?: return
        mmkv.encode(key, sanitizer(raw))
    }

    private fun rewriteFlutterJson(
        raw: String?,
        key: String,
        editor: android.content.SharedPreferences.Editor,
        sanitizer: (String) -> String
    ) {
        if (raw.isNullOrBlank()) {
            return
        }
        editor.putString(flutterKey(key), sanitizer(raw))
    }

    private fun flutterKey(key: String): String = FLUTTER_KEY_PREFIX + key

    internal fun sanitizeProviderProfilesJson(raw: String): String {
        val source = JSONArray(raw)
        val sanitized = JSONArray()
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index)
            if (item == null || !isLocalProviderObject(item)) {
                sanitized.put(source.get(index))
            }
        }
        return sanitized.toString()
    }

    internal fun sanitizeSceneBindingsJson(raw: String): String {
        return sanitizeObjectValues(raw) { value ->
            value is JSONObject && isLocalProfileId(
                value.optString("providerProfileId", value.optString("providerId", ""))
            )
        }
    }

    internal fun sanitizeProfileBucketsJson(raw: String): String {
        val source = JSONObject(raw)
        localProfileIds().forEach(source::remove)
        return source.toString()
    }

    internal fun sanitizeConversationOverridesJson(raw: String): String {
        return sanitizeObjectValues(raw) { value ->
            value is JSONObject && isLocalProfileId(value.optString("providerProfileId", ""))
        }
    }

    private fun sanitizeObjectValues(
        raw: String,
        shouldRemove: (Any?) -> Boolean
    ): String {
        val source = JSONObject(raw)
        val sanitized = JSONObject()
        source.keys().forEach { key ->
            val value = source.opt(key)
            if (!shouldRemove(value)) {
                sanitized.put(key, value)
            }
        }
        return sanitized.toString()
    }

    private fun isLocalProviderObject(value: JSONObject): Boolean {
        return isLocalProfileId(value.optString("id", "")) ||
            value.optString("sourceType", "").trim().equals("omniinfer", ignoreCase = true)
    }

    private fun isLocalProfileId(value: String?): Boolean {
        return value?.trim() in localProfileIds()
    }

    private fun localProfileIds(): Set<String> {
        return setOf(CURRENT_LOCAL_PROFILE_ID, LEGACY_LOCAL_PROFILE_ID)
    }
}

class LegacyLocalModelCleanupWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(
            "legacy_local_model_cleanup",
            Context.MODE_PRIVATE
        )
        if (prefs.getBoolean("cleanup_v1_complete", false)) {
            return Result.success()
        }

        val configurationCleaned =
            LegacyLocalModelDataCleanup.cleanupConfiguration(applicationContext)
        val filesCleaned = LegacyLocalModelDataCleanup.cleanupFiles(applicationContext)
        if (!configurationCleaned || !filesCleaned) {
            OmniLog.w(
                "LegacyLocalModelCleanup",
                "legacy local model cleanup incomplete; WorkManager will retry"
            )
            return Result.retry()
        }
        return if (prefs.edit().putBoolean("cleanup_v1_complete", true).commit()) {
            OmniLog.i("LegacyLocalModelCleanup", "legacy local model data cleanup completed")
            Result.success()
        } else {
            OmniLog.w("LegacyLocalModelCleanup", "failed to persist cleanup completion marker")
            Result.retry()
        }
    }
}
