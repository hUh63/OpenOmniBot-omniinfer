package cn.com.omnimind.bot.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAttachmentPromptSupportTest {
    @Test
    fun `buildUserMessageText includes workspace path for image attachments`() {
        val text = AgentAttachmentPromptSupport.buildUserMessageText(
            text = "请用 PaddleOCR 识别这张图",
            attachments = listOf(
                mapOf(
                    "name" to "comment.png",
                    "isImage" to true,
                    "sendToModel" to true,
                    "promptPath" to "/workspace/.omnibot/attachments/task/comment.png"
                )
            )
        )

        assertTrue(text.contains("请用 PaddleOCR 识别这张图"))
        assertTrue(text.contains("已添加到 workspace"))
        assertTrue(text.contains("- comment.png: /workspace/.omnibot/attachments/task/comment.png"))
    }
}
