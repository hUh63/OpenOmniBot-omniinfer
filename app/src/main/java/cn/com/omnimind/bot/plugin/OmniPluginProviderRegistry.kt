package cn.com.omnimind.bot.plugin

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

object OmniPluginProviderRegistry {
    private data class Registration(
        val id: String,
        val factory: (Context) -> OmniPluginProvider
    )

    private val registrations = CopyOnWriteArrayList<Registration>()
    private val sources = CopyOnWriteArrayList<RegistrationSource>()

    private data class RegistrationSource(
        val id: String,
        val factory: (Context) -> List<OmniPluginProvider>
    )

    fun register(id: String, factory: (Context) -> OmniPluginProvider) {
        require(id.isNotBlank()) { "Plugin provider registration id cannot be blank" }
        require(registrations.none { it.id == id }) {
            "Plugin provider is already registered: $id"
        }
        registrations += Registration(id = id, factory = factory)
    }

    fun registerSource(
        id: String,
        factory: (Context) -> List<OmniPluginProvider>
    ) {
        require(id.isNotBlank()) { "Plugin provider source id cannot be blank" }
        require(sources.none { it.id == id }) {
            "Plugin provider source is already registered: $id"
        }
        sources += RegistrationSource(id = id, factory = factory)
    }

    internal fun createProviders(context: Context): List<OmniPluginProvider> {
        return registrations.map { registration -> registration.factory(context) } +
            sources.flatMap { source -> source.factory(context) }
    }
}
