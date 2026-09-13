package cn.com.omnimind.bot.terminal

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EmbeddedTerminalSessionRegistry(
    private val context: Context
) {
    @Serializable
    private data class StoredSessionRecord(
        val sessionId: String,
        val workspaceId: String,
        val sessionName: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "EmbeddedTerminalRegistry"
        private const val PREFS_NAME = "embedded_terminal_sessions"
        private const val KEY_SESSIONS_JSON = "sessions_json"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun rememberSession(
        workspaceId: String,
        sessionId: String,
        sessionName: String?
    ) {
        val normalizedWorkspaceId = workspaceId.trim()
        val normalizedSessionId = sessionId.trim()
        if (normalizedWorkspaceId.isEmpty() || normalizedSessionId.isEmpty()) {
            return
        }
        val records = loadRecords().toMutableList()
        val nextRecord = StoredSessionRecord(
            sessionId = normalizedSessionId,
            workspaceId = normalizedWorkspaceId,
            sessionName = sessionName?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = records.firstOrNull { it.sessionId == normalizedSessionId }?.createdAt
                ?: System.currentTimeMillis()
        )
        val existingIndex = records.indexOfFirst { it.sessionId == normalizedSessionId }
        if (existingIndex >= 0) {
            records[existingIndex] = nextRecord
        } else {
            records.add(nextRecord)
        }
        persistRecords(records)
    }

    fun ownsSession(workspaceId: String, sessionId: String): Boolean {
        val normalizedWorkspaceId = workspaceId.trim()
        val normalizedSessionId = sessionId.trim()
        if (normalizedWorkspaceId.isEmpty() || normalizedSessionId.isEmpty()) {
            return false
        }
        return loadRecords().any { record ->
            record.workspaceId == normalizedWorkspaceId && record.sessionId == normalizedSessionId
        }
    }

    fun forgetSession(sessionId: String) {
        val normalizedSessionId = sessionId.trim()
        if (normalizedSessionId.isEmpty()) {
            return
        }
        persistRecords(
            loadRecords().filterNot { record -> record.sessionId == normalizedSessionId }
        )
    }

    fun listSessionIds(workspaceId: String): List<String> {
        val normalizedWorkspaceId = workspaceId.trim()
        if (normalizedWorkspaceId.isEmpty()) {
            return emptyList()
        }
        return loadRecords()
            .asSequence()
            .filter { record -> record.workspaceId == normalizedWorkspaceId }
            .map { record -> record.sessionId }
            .distinct()
            .toList()
    }

    private fun loadRecords(): List<StoredSessionRecord> {
        val raw = prefs.getString(KEY_SESSIONS_JSON, null)?.trim().orEmpty()
        if (raw.isEmpty()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<StoredSessionRecord>>(raw)
        }.getOrElse { error ->
            OmniLog.e(TAG, "Failed to parse embedded terminal session registry", error)
            emptyList()
        }
    }

    private fun persistRecords(records: List<StoredSessionRecord>) {
        prefs.edit().putString(KEY_SESSIONS_JSON, json.encodeToString(records)).apply()
    }
}
