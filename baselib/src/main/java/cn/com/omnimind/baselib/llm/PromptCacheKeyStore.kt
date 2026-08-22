package cn.com.omnimind.baselib.llm

import android.content.Context
import java.util.UUID

/**
 * Builds an anonymous, stable prompt-cache routing key for a local conversation.
 *
 * The random install scope prevents unrelated users whose local databases both
 * contain (for example) conversation 1 from being routed under the same key.
 * It contains no account, device, prompt, or API-key data.
 */
object PromptCacheKeyStore {
    private const val PREFS_NAME = "omnibot_prompt_cache"
    private const val KEY_INSTALL_SCOPE = "install_scope_v1"
    private const val INSTALL_SCOPE_LENGTH = 20

    @Synchronized
    fun forConversation(context: Context, conversationId: Long?): String? {
        val normalizedConversationId = conversationId?.takeIf { it > 0L } ?: return null
        val preferences = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val installScope = preferences.getString(KEY_INSTALL_SCOPE, null)
            ?.takeIf(::isValidInstallScope)
            ?: newInstallScope().also { generated ->
                preferences.edit().putString(KEY_INSTALL_SCOPE, generated).apply()
            }
        return buildConversationKey(installScope, normalizedConversationId)
    }

    internal fun buildConversationKey(installScope: String, conversationId: Long): String {
        require(isValidInstallScope(installScope)) { "installScope must be 20 lowercase hex characters" }
        require(conversationId > 0L) { "conversationId must be positive" }
        // At Long.MAX_VALUE this is exactly 64 characters, keeping the key
        // compatible with gateways that enforce a 64-character identifier limit.
        return "omnibot:v1:$installScope:conversation:$conversationId"
    }

    private fun newInstallScope(): String = UUID.randomUUID()
        .toString()
        .replace("-", "")
        .take(INSTALL_SCOPE_LENGTH)

    private fun isValidInstallScope(value: String): Boolean {
        return value.length == INSTALL_SCOPE_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
