package cn.com.omnimind.assists.task.recording

import cn.com.omnimind.baselib.runlog.actionOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualRecordingJournalTest {
    @Test
    fun appendKeepsStableOneBasedOrder() {
        val journal = ManualRecordingJournal()

        assertEquals(1, journal.append(action("click", "点击搜索")))
        assertEquals(2, journal.append(action("swipe", "向上滑动")))
        assertEquals(listOf("click", "swipe"), journal.snapshot().map { it.action.tool })
    }

    @Test
    fun summaryUsesSingleJournalSnapshot() {
        val journal = ManualRecordingJournal()
        journal.append(action("click", "点击搜索"))
        journal.append(action("input_text", "输入关键词"))

        assertEquals(
            "用户在接管期间手动完成了 2 步操作：点击搜索；输入关键词。请基于当前屏幕继续执行原任务。",
            journal.summary(),
        )
    }

    private fun action(name: String, summary: String) = ManualRecordedAction(
        action = actionOf(name),
        title = summary,
        beforeState = null,
        afterState = null,
        startedAtMs = 1L,
        finishedAtMs = 2L,
        summary = summary,
    )
}
