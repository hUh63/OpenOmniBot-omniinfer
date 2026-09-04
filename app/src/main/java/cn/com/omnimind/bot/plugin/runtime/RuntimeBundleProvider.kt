package cn.com.omnimind.bot.plugin.runtime

import android.content.Context
import cn.com.omnimind.bot.plugin.OmniPlugin
import cn.com.omnimind.bot.plugin.OmniPluginDescriptor
import cn.com.omnimind.bot.plugin.OmniPluginProvider
import java.util.concurrent.ConcurrentHashMap

enum class RuntimeBundlePrepareMode {
    INSTALL,
    UPDATE,
}

interface RuntimeBundleAdapter {
    suspend fun prepare(mode: RuntimeBundlePrepareMode)

    suspend fun remove()

    fun open(): OmniPlugin
}

class RuntimeBundleProvider(
    definition: RuntimeBundleDefinition,
    private val adapter: RuntimeBundleAdapter,
) : OmniPluginProvider {
    override val descriptor: OmniPluginDescriptor = definition.descriptor

    override suspend fun install() = adapter.prepare(RuntimeBundlePrepareMode.INSTALL)

    override suspend fun update() = adapter.prepare(RuntimeBundlePrepareMode.UPDATE)

    override suspend fun uninstall() = adapter.remove()

    override fun create(): OmniPlugin = adapter.open()
}

object RuntimeBundleAdapterRegistry {
    private val factories = ConcurrentHashMap<
        String,
        (Context, RuntimeBundleDefinition) -> RuntimeBundleAdapter
        >()

    fun register(
        adapterId: String,
        factory: (Context, RuntimeBundleDefinition) -> RuntimeBundleAdapter,
    ) {
        require(adapterId.isNotBlank()) { "Runtime bundle adapter id cannot be blank" }
        require(factories.putIfAbsent(adapterId, factory) == null) {
            "Runtime bundle adapter is already registered: $adapterId"
        }
    }

    fun createProviders(
        context: Context,
        catalog: RuntimeBundleCatalog,
    ): List<OmniPluginProvider> = catalog.bundles.map { definition ->
        val factory = factories[definition.adapterId]
            ?: error(
                "Runtime bundle ${definition.descriptor.id} requires unknown adapter " +
                    definition.adapterId
            )
        RuntimeBundleProvider(definition, factory(context, definition))
    }
}
