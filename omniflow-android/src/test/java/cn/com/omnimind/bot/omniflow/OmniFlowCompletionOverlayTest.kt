package cn.com.omnimind.bot.omniflow

import org.junit.Assert.assertEquals
import org.junit.Test

class OmniFlowCompletionOverlayTest {
    @Test
    fun `successful VLM completion dismisses controls immediately`() {
        assertEquals(
            0L,
            completionOverlayVisibleMs("vlm", mapOf("success" to true)),
        )
    }

    @Test
    fun `failed VLM completion also dismisses controls immediately`() {
        assertEquals(
            0L,
            completionOverlayVisibleMs("vlm", mapOf("success" to false)),
        )
    }

    @Test
    fun `function replay keeps the short completion state`() {
        assertEquals(
            900L,
            completionOverlayVisibleMs("function", mapOf("success" to true)),
        )
    }

    @Test
    fun `manual completion is persisted as successful completion`() {
        val result = manualCompletionResult(
            runId = "gui-1",
            startedAtMs = 1_000L,
            source = "vlm",
            functionId = "run_gui",
            finishedAtMs = 2_500L,
        )

        assertEquals(true, result["success"])
        assertEquals("succeeded", result["status"])
        assertEquals("manual_finished", result["done_reason"])
        assertEquals(1_500L, result["duration_ms"])
    }

    @Test
    fun `window screenshot keeps execution controls visible`() {
        assertEquals(false, shouldSuppressOverlayForScreenshot(true, true))
    }

    @Test
    fun `display screenshot hides execution controls on legacy Android`() {
        assertEquals(true, shouldSuppressOverlayForScreenshot(true, false))
    }
}
