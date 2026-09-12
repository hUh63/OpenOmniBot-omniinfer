package cn.com.omnimind.baselib.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogStoreTest {
    @Test
    fun `suppresses assists channel binding lifecycle noise`() {
        val entry = RuntimeLogEntry(
            level = "ERROR",
            tag = "[AssistsCoreManager]",
            message = "setChannel",
        )

        assertTrue(entry.isSuppressedRuntimeLogNoise())
    }

    @Test
    fun `keeps real assists errors`() {
        val entry = RuntimeLogEntry(
            level = "ERROR",
            tag = "[AssistsCoreManager]",
            message = "setChannel failed",
            stackTrace = "java.lang.IllegalStateException",
        )

        assertFalse(entry.isSuppressedRuntimeLogNoise())
    }
}
