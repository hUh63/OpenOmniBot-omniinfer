package cn.com.omnimind.bot.im

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ImChannelStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "im_channel_settings",
        Context.MODE_PRIVATE
    )

    fun loadSettings(): ImChannelSettings {
        return ImChannelSettings(
            telegram = TelegramImConfig(
                enabled = prefs.getBoolean(KEY_TELEGRAM_ENABLED, false),
                botToken = prefs.getString(KEY_TELEGRAM_TOKEN, "") ?: "",
                apiBaseUrl = prefs.getString(KEY_TELEGRAM_API_BASE, "https://api.telegram.org")
                    ?: "https://api.telegram.org",
                allowedChatIds = parseList(prefs.getString(KEY_TELEGRAM_ALLOWED, "") ?: ""),
                chunkSize = prefs.getInt(KEY_TELEGRAM_CHUNK_SIZE, 3900),
                dropPendingUpdates = prefs.getBoolean(KEY_TELEGRAM_DROP_PENDING, false)
            ).normalized(),
            wechat = WechatImConfig(
                enabled = prefs.getBoolean(KEY_WECHAT_ENABLED, false),
                token = prefs.getString(KEY_WECHAT_TOKEN, "") ?: "",
                baseUrl = prefs.getString(KEY_WECHAT_BASE_URL, "https://ilinkai.weixin.qq.com")
                    ?: "https://ilinkai.weixin.qq.com",
                botType = prefs.getString(KEY_WECHAT_BOT_TYPE, "3") ?: "3",
                version = prefs.getString(KEY_WECHAT_VERSION, "1.0.0") ?: "1.0.0",
                chunkSize = prefs.getInt(KEY_WECHAT_CHUNK_SIZE, 3000)
            ).normalized()
        )
    }

    fun saveTelegram(config: TelegramImConfig): ImChannelSettings {
        val normalized = config.normalized()
        prefs.edit()
            .putBoolean(KEY_TELEGRAM_ENABLED, normalized.enabled)
            .putString(KEY_TELEGRAM_TOKEN, normalized.botToken)
            .putString(KEY_TELEGRAM_API_BASE, normalized.apiBaseUrl)
            .putString(KEY_TELEGRAM_ALLOWED, normalized.allowedChatIds.joinToString("\n"))
            .putInt(KEY_TELEGRAM_CHUNK_SIZE, normalized.chunkSize)
            .putBoolean(KEY_TELEGRAM_DROP_PENDING, normalized.dropPendingUpdates)
            .apply()
        return loadSettings()
    }

    fun saveWechat(config: WechatImConfig): ImChannelSettings {
        val normalized = config.normalized()
        prefs.edit()
            .putBoolean(KEY_WECHAT_ENABLED, normalized.enabled)
            .putString(KEY_WECHAT_TOKEN, normalized.token)
            .putString(KEY_WECHAT_BASE_URL, normalized.baseUrl)
            .putString(KEY_WECHAT_BOT_TYPE, normalized.botType)
            .putString(KEY_WECHAT_VERSION, normalized.version)
            .putInt(KEY_WECHAT_CHUNK_SIZE, normalized.chunkSize)
            .apply()
        return loadSettings()
    }

    fun saveWechatCredentials(token: String, baseUrl: String?) {
        prefs.edit()
            .putBoolean(KEY_WECHAT_ENABLED, true)
            .putString(KEY_WECHAT_TOKEN, token.trim())
            .apply {
                val normalizedBaseUrl = baseUrl?.trim().orEmpty()
                if (normalizedBaseUrl.isNotEmpty()) {
                    putString(KEY_WECHAT_BASE_URL, normalizedBaseUrl)
                }
            }
            .apply()
    }

    fun setChannelEnabled(channel: ImChannelType, enabled: Boolean): ImChannelSettings {
        val key = when (channel) {
            ImChannelType.TELEGRAM -> KEY_TELEGRAM_ENABLED
            ImChannelType.WECHAT -> KEY_WECHAT_ENABLED
        }
        prefs.edit().putBoolean(key, enabled).apply()
        return loadSettings()
    }

    fun saveSession(session: ImPeerSession) {
        val sessions = listSessions().filterNot { it.key == session.key }.toMutableList()
        sessions += session.copy(updatedAt = System.currentTimeMillis())
        persistSessions(sessions)
    }

    fun getSession(channel: ImChannelType, peerId: String): ImPeerSession? {
        val key = "${channel.id}:$peerId"
        return listSessions().firstOrNull { it.key == key }
    }

    fun clearSession(channel: ImChannelType, peerId: String) {
        val key = "${channel.id}:$peerId"
        persistSessions(listSessions().filterNot { it.key == key })
    }

    fun clearPeerSessions() {
        prefs.edit().remove(KEY_SESSIONS).apply()
    }

    fun listSessions(): List<ImPeerSession> {
        val raw = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val result = mutableListOf<ImPeerSession>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val channel = ImChannelType.fromId(item.optString("channel")) ?: continue
            val peerId = item.optString("peerId").trim()
            if (peerId.isEmpty()) continue
            result += ImPeerSession(
                channel = channel,
                peerId = peerId,
                displayName = item.optString("displayName"),
                conversationId = item.optLong("conversationId", 0L),
                mode = normalizeImConversationMode(item.optString("mode")) ?: "normal",
                activeTaskId = item.optString("activeTaskId")
                    .trim()
                    .takeIf { it.isNotEmpty() },
                awaitingInput = item.optBoolean("awaitingInput", false),
                updatedAt = item.optLong("updatedAt", 0L)
            )
        }
        return result
    }

    fun clearActiveTask(taskId: String) {
        val sessions = listSessions().map { session ->
            if (session.activeTaskId == taskId) {
                session.copy(activeTaskId = null, awaitingInput = false)
            } else {
                session
            }
        }
        persistSessions(sessions)
    }

    fun markAwaitingInput(taskId: String, awaitingInput: Boolean) {
        val sessions = listSessions().map { session ->
            if (session.activeTaskId == taskId) {
                session.copy(awaitingInput = awaitingInput)
            } else {
                session
            }
        }
        persistSessions(sessions)
    }

    private fun persistSessions(sessions: List<ImPeerSession>) {
        val sorted = sessions.sortedByDescending { it.updatedAt }.take(MAX_SESSION_COUNT)
        val array = JSONArray()
        sorted.forEach { session ->
            array.put(
                JSONObject()
                    .put("channel", session.channel.id)
                    .put("peerId", session.peerId)
                    .put("displayName", session.displayName)
                    .put("conversationId", session.conversationId)
                    .put("mode", session.mode)
                    .put("activeTaskId", session.activeTaskId ?: "")
                    .put("awaitingInput", session.awaitingInput)
                    .put("updatedAt", session.updatedAt)
            )
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun parseList(value: String): Set<String> {
        return value.split(',', '\n', ';', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    companion object {
        private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        private const val KEY_TELEGRAM_TOKEN = "telegram_token"
        private const val KEY_TELEGRAM_API_BASE = "telegram_api_base"
        private const val KEY_TELEGRAM_ALLOWED = "telegram_allowed_chat_ids"
        private const val KEY_TELEGRAM_CHUNK_SIZE = "telegram_chunk_size"
        private const val KEY_TELEGRAM_DROP_PENDING = "telegram_drop_pending_updates"

        private const val KEY_WECHAT_ENABLED = "wechat_enabled"
        private const val KEY_WECHAT_TOKEN = "wechat_token"
        private const val KEY_WECHAT_BASE_URL = "wechat_base_url"
        private const val KEY_WECHAT_BOT_TYPE = "wechat_bot_type"
        private const val KEY_WECHAT_VERSION = "wechat_version"
        private const val KEY_WECHAT_CHUNK_SIZE = "wechat_chunk_size"

        private const val KEY_SESSIONS = "peer_sessions"
        private const val MAX_SESSION_COUNT = 200
    }
}
