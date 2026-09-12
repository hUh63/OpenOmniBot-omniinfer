package cn.com.omnimind.bot.omniflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniFlowPythonRuntimeStatusTest {
    @Test
    fun `available transfer runtime is accepted`() {
        assertTrue(declaredOmniTransferRuntimeStatus(true))
    }

    @Test
    fun `declared degraded transfer runtime allows VLM fallback`() {
        assertFalse(declaredOmniTransferRuntimeStatus(false))
    }

    @Test
    fun `missing transfer runtime status is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            declaredOmniTransferRuntimeStatus(null)
        }
    }
}
