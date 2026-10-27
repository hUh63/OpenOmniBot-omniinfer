package cn.com.omnimind.bot.plugin

import android.content.Context

internal class SharedPreferencesOmniPluginStateStore(context: Context) : OmniPluginStateStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): List<OmniPluginStoredState> {
        return preferences.getStringSet(STATES_KEY, emptySet()).orEmpty()
            .mapNotNull(::decode)
            // 按「用户启用插件的操作顺序」排序；seq 相同的（历史数据）退回 pluginId，
            // 保证结果稳定、不会每次启动抖动。
            .sortedWith(compareBy({ it.activationSeq }, { it.pluginId }))
    }

    override fun readWithDefaults(
        defaults: List<OmniPluginStoredState>
    ): List<OmniPluginStoredState> {
        if (preferences.getBoolean(DEFAULTS_SEEDED_KEY, false)) {
            return read()
        }
        val current = read()
        val currentIds = current.mapTo(mutableSetOf()) { it.pluginId }
        val seeded = (current + defaults.filter { currentIds.add(it.pluginId) })
            .sortedWith(compareBy({ it.activationSeq }, { it.pluginId }))
        val encoded = seeded.mapTo(linkedSetOf(), ::encode)
        check(
            preferences.edit()
                .putStringSet(STATES_KEY, encoded)
                .putBoolean(DEFAULTS_SEEDED_KEY, true)
                .commit()
        ) {
            "Failed to seed default plugin state"
        }
        return seeded
    }

    override fun write(states: List<OmniPluginStoredState>) {
        val encoded = states.mapTo(linkedSetOf(), ::encode)
        check(preferences.edit().putStringSet(STATES_KEY, encoded).commit()) {
            "Failed to persist plugin state"
        }
    }

    private fun encode(state: OmniPluginStoredState): String {
        return listOf(
            state.pluginId,
            if (state.enabled) ENABLED else DISABLED,
            if (state.installPending) PENDING else READY,
            // 第 4 段是启用顺序。老版本只写 3 段，读取时按 seq=0 兼容。
            state.activationSeq.toString(),
        ).joinToString(SEPARATOR)
    }

    private fun decode(value: String): OmniPluginStoredState? {
        val parts = value.split(SEPARATOR)
        if (parts.size !in 2..4) return null
        val pluginId = parts[0].takeIf(String::isNotBlank) ?: return null
        val enabled = when (parts[1]) {
            ENABLED -> true
            DISABLED -> false
            else -> return null
        }
        val installPending = when (parts.getOrNull(2)) {
            null, READY -> false
            PENDING -> true
            else -> return null
        }
        val activationSeq = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        return OmniPluginStoredState(
            pluginId = pluginId,
            enabled = enabled,
            installPending = installPending,
            activationSeq = activationSeq,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "omni_plugin_platform"
        const val STATES_KEY = "installed_plugins"
        const val DEFAULTS_SEEDED_KEY = "default_plugins_seeded_v2"
        const val SEPARATOR = "|"
        const val ENABLED = "1"
        const val DISABLED = "0"
        const val PENDING = "pending"
        const val READY = "ready"
    }
}
