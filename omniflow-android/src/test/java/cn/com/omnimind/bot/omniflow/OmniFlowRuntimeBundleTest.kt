package cn.com.omnimind.bot.omniflow

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OmniFlowRuntimeBundleTest {
    @Test
    fun `manifest parses pinned skill runtime identity`() {
        val manifest = parseOmniFlowRuntimeManifest(
            ByteArrayInputStream(
                """
                    runtime.version=2026.07.31.1
                    runtime.protocol=2025-11-25
                    runtime.capabilities=initialize,tools/call,tools/list
                    runtime.python=3.12
                    bridge.contract.sha256=${"d".repeat(64)}
                    omniflow.commit=flow
                    omniflow.source.sha256=${"b".repeat(64)}
                    omnitransfer.commit=transfer
                    omnitransfer.source.sha256=${"c".repeat(64)}
                    omnitransfer.checkpoint=checkpoints/matcher.npz
                    numpy.version=2.2.6
                    json_repair.version=0.61.7
                """.trimIndent().toByteArray(),
            ),
        )

        assertEquals("2026.07.31.1", manifest.version)
        assertEquals("2025-11-25", manifest.protocol)
        assertEquals(setOf("initialize", "tools/call", "tools/list"), manifest.capabilities)
        assertEquals("d".repeat(64), manifest.bridgeContractSha256)
        assertEquals("flow", manifest.omniFlowCommit)
        assertEquals("b".repeat(64), manifest.omniFlowSourceSha256)
        assertEquals("transfer", manifest.omniTransferCommit)
        assertEquals("c".repeat(64), manifest.omniTransferSourceSha256)
        assertEquals("checkpoints/matcher.npz", manifest.omniTransferCheckpoint)
        assertEquals("2.2.6", manifest.numpyVersion)
        assertEquals("0.61.7", manifest.jsonRepairVersion)
    }

    @Test
    fun `manifest rejects checkpoint path traversal`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseOmniFlowRuntimeManifest(
                ByteArrayInputStream(
                    """
                        runtime.version=1
                        runtime.protocol=2025-11-25
                        runtime.capabilities=initialize
                        runtime.python=3.12
                        bridge.contract.sha256=${"d".repeat(64)}
                        omniflow.commit=flow
                        omniflow.source.sha256=${"b".repeat(64)}
                        omnitransfer.commit=transfer
                        omnitransfer.source.sha256=${"c".repeat(64)}
                        omnitransfer.checkpoint=../escape.npz
                        numpy.version=2.2.6
                        json_repair.version=0.61.7
                    """.trimIndent().toByteArray(),
                ),
            )
        }

        assertEquals("omniflow_runtime_checkpoint_path_invalid", error.message)
    }
}
