package cn.com.omnimind.assists

import cn.com.omnimind.baselib.runlog.CanonicalRunLogRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRecordingRunLogRecoveryTest {
    @Test
    fun unfinishedManualRunWithRecordedActionsCanBeRecovered() {
        val record = CanonicalRunLogRecord(
            runId = "manual-run",
            finishedAtMs = null,
            steps = listOf(
                mapOf(
                    "action" to mapOf("tool" to "click", "args" to emptyMap<String, Any?>()),
                    "metadata" to mapOf(
                        "source" to "human_trajectory",
                        "recording_backend" to "overlay_touch",
                    ),
                ),
            ),
            diagnostics = mapOf("source" to "human_trajectory"),
        )

        val decision = ManualRecordingRunLogRecovery.decisionFor(record)

        assertNotNull(decision)
        assertTrue(decision!!.success)
        assertEquals(1, decision.replayableActionCount)
        assertEquals(
            ManualRecordingRunLogRecovery.DONE_REASON_RECOVERED_AFTER_RESTART,
            decision.doneReason,
        )
    }
}
