package cn.com.omnimind.bot.omniflow

import org.junit.Assert.assertEquals
import org.junit.Test

class OmniVlmMaxStepsTest {
    @Test
    fun `VLM defaults to thirty model steps`() {
        val request = OmniVlmPlugin.Request(goal = "open settings")

        assertEquals(30, request.maxSteps)
        assertEquals(30, request.runGuiArguments()["max_steps"])
    }
}
