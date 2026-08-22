package cn.com.omnimind.bot.omniflow

import cn.com.omnimind.baselib.runlog.Action
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniFlowFunctionReplayImeTest {
    @Test
    fun `function replay skips keyboard back when ime is already hidden`() {
        assertTrue(
            shouldSkipFunctionReplayImeBack(
                source = "function",
                previousTool = "input_text",
                action = Action("press_key", mapOf("key" to "back")),
                inputMethodTop = null,
            ),
        )
    }

    @Test
    fun `function replay dispatches keyboard back when ime is visible`() {
        assertFalse(
            shouldSkipFunctionReplayImeBack(
                source = "function",
                previousTool = "input_text",
                action = Action("press_key", mapOf("key" to "back")),
                inputMethodTop = 780,
            ),
        )
    }

    @Test
    fun `online vlm back navigation is not skipped`() {
        assertFalse(
            shouldSkipFunctionReplayImeBack(
                source = "vlm",
                previousTool = "input_text",
                action = Action("press_key", mapOf("key" to "back")),
                inputMethodTop = null,
            ),
        )
    }

    @Test
    fun `ordinary function back navigation is not skipped`() {
        assertFalse(
            shouldSkipFunctionReplayImeBack(
                source = "function",
                previousTool = "click",
                action = Action("press_key", mapOf("key" to "back")),
                inputMethodTop = null,
            ),
        )
    }
}
