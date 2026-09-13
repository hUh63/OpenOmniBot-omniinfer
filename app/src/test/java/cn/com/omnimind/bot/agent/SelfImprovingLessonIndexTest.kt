package cn.com.omnimind.bot.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SelfImprovingLessonIndexTest {

    private fun writeErrors(skillsRoot: File, body: String) {
        val dir = File(File(skillsRoot, "self-improving-agent"), "data")
        dir.mkdirs()
        File(dir, "ERRORS.md").writeText("# Errors\n$body")
    }

    @Test
    fun `extracts newest-first lessons with tool name and fix`() {
        val root = Files.createTempDirectory("sia-skills").toFile()
        writeErrors(
            root,
            """
            ## [ERR-20260101-AAA] terminal_execute

            **状态**: pending

            ### 摘要
            command not found: foo

            ### 建议修复
            （待补充）

            ---
            ## [ERR-20260102-BBB] browser_use

            **状态**: resolved

            ### 摘要
            导航超时，需要先 navigate 再 screenshot

            ### 建议修复
            先 navigate 等待加载完成再截图

            ---
            """.trimIndent()
        )

        val lessons = SelfImprovingSkillFailureHook.collectSearchableLessons(root)

        assertEquals(2, lessons.size)
        // Newest block first; fix appended only when filled (not the placeholder).
        assertEquals(
            "[browser_use] 导航超时，需要先 navigate 再 screenshot → 先 navigate 等待加载完成再截图",
            lessons[0]
        )
        assertEquals("[terminal_execute] command not found: foo", lessons[1])
    }

    @Test
    fun `missing errors file yields empty`() {
        val root = Files.createTempDirectory("sia-empty").toFile()
        assertTrue(SelfImprovingSkillFailureHook.collectSearchableLessons(root).isEmpty())
    }
}
