package cn.com.omnimind.bot.plugin

import android.content.ContextWrapper
import org.junit.Assert.assertSame
import org.junit.Test

class OmniPluginProviderRegistryTest {
    @Test
    fun `provider sources contribute manifest discovered plugins`() {
        val provider = object : OmniPluginProvider {
            override val descriptor = OmniPluginDescriptor(
                id = "com.omnimind.registry-test",
                name = "Registry Test",
                version = "1.0.0",
                description = "test",
                publisher = "OmniMind",
            )

            override fun create(): OmniPlugin = object : OmniPlugin {}
        }
        OmniPluginProviderRegistry.registerSource("registry-test-source") {
            listOf(provider)
        }

        val discovered = OmniPluginProviderRegistry.createProviders(TestContext)

        assertSame(
            provider,
            discovered.single { it.descriptor.id == provider.descriptor.id },
        )
    }

    private object TestContext : ContextWrapper(null)
}
