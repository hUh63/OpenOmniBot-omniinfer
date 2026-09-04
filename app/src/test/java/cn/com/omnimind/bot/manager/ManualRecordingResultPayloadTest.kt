package cn.com.omnimind.bot.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRecordingResultPayloadTest {
    @Test
    fun `function conversion failure does not turn recording into failure`() {
        val runLog = mapOf(
            "schema_version" to "omniflow.canonical_run_log.v1",
            "run_id" to "manual-run-1",
        )
        val payload = buildManualRecordingFinalizedPayload(
            recordingSuccess = true,
            phase = "finished",
            diagnostics = emptyMap(),
            recordingErrorMessage = null,
            runLog = runLog,
            conversion = mapOf(
                "success" to false,
                "error_code" to "RUN_LOG_NO_REPLAYABLE_STEPS",
                "error_message" to "RunLog has no replayable steps",
            ),
        )

        assertEquals(true, payload["success"])
        assertEquals(runLog, payload["run_log"])
        assertEquals(
            mapOf(
                "code" to "RUN_LOG_NO_REPLAYABLE_STEPS",
                "message" to "RunLog has no replayable steps",
            ),
            payload["function_error"],
        )
        assertFalse(payload.containsKey("function"))
        assertNoLegacyFields(payload)
    }

    @Test
    fun `successful conversion exposes only canonical function`() {
        val function = mapOf(
            "schema_version" to "omniflow.function.v2",
            "function_id" to "manual_function",
        )
        val payload = buildManualRecordingFinalizedPayload(
            recordingSuccess = true,
            phase = "finished",
            diagnostics = emptyMap(),
            recordingErrorMessage = null,
            runLog = mapOf("run_id" to "manual-run-2"),
            conversion = mapOf("success" to true, "function" to function),
        )

        assertEquals(function, payload["function"])
        assertFalse(payload.containsKey("function_error"))
        assertNoLegacyFields(payload)
    }

    private fun assertNoLegacyFields(payload: Map<String, Any?>) {
        setOf(
            "recording_success",
            "conversion_success",
            "function_registered",
            "function_id",
            "conversion",
            "function_kind",
            "asset_state",
            "token_usage_total",
        ).forEach { field ->
            assertFalse("Unexpected legacy field: $field", payload.containsKey(field))
        }
        assertTrue(payload.keys.all { it == it.lowercase() })
    }
}
