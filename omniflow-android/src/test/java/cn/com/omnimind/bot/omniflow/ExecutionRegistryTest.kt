package cn.com.omnimind.bot.omniflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionRegistryTest {
    @Test
    fun `parent run id stops active recall child`() {
        val registry = ExecutionRegistry()
        var stopped = false
        registry.begin("gui-123-recall") { stopped = true }

        assertTrue(registry.stop("gui-123"))
        assertTrue(stopped)
    }

    @Test
    fun `unrelated run id cannot stop active execution`() {
        val registry = ExecutionRegistry()
        var stopped = false
        registry.begin("gui-123-recall") { stopped = true }

        assertFalse(registry.stop("gui-456"))
        assertFalse(stopped)
    }
}
