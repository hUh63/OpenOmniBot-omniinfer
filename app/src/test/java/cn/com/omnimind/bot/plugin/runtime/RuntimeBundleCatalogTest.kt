package cn.com.omnimind.bot.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBundleCatalogTest {
    @Test
    fun `catalog parses descriptor runtime skill and presentation`() {
        val catalog = RuntimeBundleCatalog.parse(
            catalogJson().replace(
                "\"adapter\": \"omniflow_android_gui\",",
                "\"adapter\": \"omniflow_android_gui\",\n              \"required\": true,",
            )
        )

        val bundle = catalog.require("com.omnimind.android-gui")
        assertEquals("Android GUI", bundle.descriptor.name)
        assertTrue(bundle.descriptor.required)
        assertEquals("omniflow_android_gui", bundle.adapterId)
        assertEquals("omniflow-gui-runtime", bundle.runtimeSkill.id)
        assertEquals(
            "Android GUI is ready",
            bundle.descriptor.presentation["ready"]
                ?.let { it as kotlinx.serialization.json.JsonObject }
                ?.get("title")
                ?.let { it as kotlinx.serialization.json.JsonObject }
                ?.get("en")
                ?.toString()
                ?.trim('"'),
        )
    }

    @Test
    fun `catalog rejects duplicate plugin ids`() {
        val duplicate = catalogJson().replace(
            """    }
  ]""",
            """    },
    ${catalogJson().substringAfter("\"plugins\": [").substringBeforeLast("]").trim()}
  ]""",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            RuntimeBundleCatalog.parse(duplicate)
        }

        assertTrue(error.message.orEmpty().contains("Duplicate runtime bundle id"))
    }

    @Test
    fun `catalog rejects paths that escape the packaged skill`() {
        val invalid = catalogJson().replace(
            "plugins/android-gui",
            "../android-gui",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            RuntimeBundleCatalog.parse(invalid)
        }

        assertTrue(error.message.orEmpty().contains("cannot escape"))
    }

    @Test
    fun `catalog filters packaged plugins by build profile`() {
        val investorOnly = catalogJson().replace(
            "\"adapter\": \"omniflow_android_gui\",",
            "\"adapter\": \"omniflow_android_gui\",\n              \"profiles\": [\"investor\"],",
        )

        assertTrue(RuntimeBundleCatalog.parse(investorOnly, "main").bundles.isEmpty())
        assertEquals(
            1,
            RuntimeBundleCatalog.parse(investorOnly, "investor").bundles.size,
        )
    }

    private fun catalogJson(): String =
        """
        {
          "schemaVersion": 1,
          "plugins": [
            {
              "id": "com.omnimind.android-gui",
              "name": "Android GUI",
              "version": "1.0.0",
              "publisher": "OmniMind",
              "kind": "runtime_bundle",
              "adapter": "omniflow_android_gui",
              "runtimeSkill": {
                "id": "omniflow-gui-runtime",
                "packagedAssetPath": "plugins/android-gui"
              },
              "presentation": {
                "ready": {
                  "title": {"zh": "已就绪", "en": "Android GUI is ready"}
                }
              }
            }
          ]
        }
        """.trimIndent()
}
