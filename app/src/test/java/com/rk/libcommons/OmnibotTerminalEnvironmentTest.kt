package com.rk.libcommons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OmnibotTerminalEnvironmentTest {
    @Test
    fun `user variables keep valid environment names only`() {
        val normalized = OmnibotTerminalEnvironment.normalizeVariables(
            linkedMapOf(
                "EXAMPLE" to "value",
                "2INVALID" to "ignored",
                "ANOTHER_VALUE" to "kept"
            )
        )

        assertEquals("value", normalized["EXAMPLE"])
        assertEquals("kept", normalized["ANOTHER_VALUE"])
        assertFalse(normalized.containsKey("2INVALID"))
    }
}
