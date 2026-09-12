package cn.com.omnimind.bot.plugin.runtime

import cn.com.omnimind.bot.plugin.OmniPlugin
import cn.com.omnimind.bot.plugin.OmniPluginDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RuntimeBundleProviderTest {
    @Test
    fun `provider delegates lifecycle through one runtime adapter`() = runBlocking {
        val plugin = object : OmniPlugin {}
        val adapter = RecordingAdapter(plugin)
        val definition = RuntimeBundleDefinition(
            descriptor = OmniPluginDescriptor(
                id = "com.omnimind.test-runtime",
                name = "Test Runtime",
                version = "1.0.0",
                description = "test",
                publisher = "OmniMind",
            ),
            adapterId = "test",
            runtimeSkill = RuntimeSkillSpec(
                componentId = "com.omnimind.test-runtime",
                componentVersion = "1.0.0",
                id = "test-runtime",
                packagedAssetPath = "plugins/test-runtime",
            ),
        )
        val provider = RuntimeBundleProvider(definition, adapter)

        provider.install()
        provider.update()
        assertSame(plugin, provider.create())
        provider.uninstall()

        assertEquals(
            listOf(
                "prepare:INSTALL",
                "prepare:UPDATE",
                "open",
                "remove",
            ),
            adapter.calls,
        )
    }

    private class RecordingAdapter(
        private val plugin: OmniPlugin,
    ) : RuntimeBundleAdapter {
        val calls = mutableListOf<String>()

        override suspend fun prepare(mode: RuntimeBundlePrepareMode) {
            calls += "prepare:$mode"
        }

        override suspend fun remove() {
            calls += "remove"
        }

        override fun open(): OmniPlugin {
            calls += "open"
            return plugin
        }
    }
}
