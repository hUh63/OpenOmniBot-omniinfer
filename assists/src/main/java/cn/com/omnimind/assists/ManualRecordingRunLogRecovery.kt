package cn.com.omnimind.assists

import cn.com.omnimind.baselib.runlog.CanonicalRunLogRecord
import cn.com.omnimind.baselib.runlog.OobActionSchema

data class ManualRecordingRunLogRecoveryDecision(
    val success: Boolean,
    val doneReason: String,
    val errorMessage: String?,
    val replayableActionCount: Int,
    val diagnostics: Map<String, Any?>
)

object ManualRecordingRunLogRecovery {
    const val HUMAN_TRAJECTORY_SOURCE = "human_trajectory"
    const val DONE_REASON_RECOVERED_AFTER_RESTART = "recovered_after_restart"
    const val DONE_REASON_EMPTY_AFTER_RESTART = "empty_recording_after_restart"
    const val EMPTY_RECORDING_ERROR = "未记录到可复用的人类操作"

    private val replayableManualActions = setOf(
        OobActionSchema.TOOL_CLICK,
        OobActionSchema.TOOL_LONG_PRESS,
        OobActionSchema.TOOL_SWIPE,
        "scroll",
        OobActionSchema.TOOL_INPUT_TEXT,
        OobActionSchema.TOOL_PRESS_KEY,
        OobActionSchema.TOOL_WAIT,
    )

    fun decisionFor(record: CanonicalRunLogRecord): ManualRecordingRunLogRecoveryDecision? {
        if (record.source != HUMAN_TRAJECTORY_SOURCE || record.finishedAtMs != null) {
            return null
        }
        val actionSteps = record.steps.filter(::isManualReplayActionStep)
        val success = actionSteps.isNotEmpty()
        return ManualRecordingRunLogRecoveryDecision(
            success = success,
            doneReason = if (success) {
                DONE_REASON_RECOVERED_AFTER_RESTART
            } else {
                DONE_REASON_EMPTY_AFTER_RESTART
            },
            errorMessage = if (success) null else EMPTY_RECORDING_ERROR,
            replayableActionCount = actionSteps.size,
            diagnostics = recoveredManualRecordingDiagnostics(record.steps)
        )
    }

    fun isManualReplayActionStep(step: Map<String, Any?>): Boolean {
        val metadata = step["metadata"] as? Map<*, *>
        val source = firstNonBlank(metadata?.get("source")).lowercase()
        if (source !in setOf("human_trajectory", "human_takeover")) {
            return false
        }
        val canonicalAction = step["action"] as? Map<*, *>
        val action = firstNonBlank(canonicalAction?.get("tool")).lowercase()
        return action in replayableManualActions
    }

    fun recoveredManualRecordingDiagnostics(steps: List<Map<String, Any?>>): Map<String, Any?> {
        val actionSteps = steps.filter(::isManualReplayActionStep)
        val backends = actionSteps.mapNotNull(::recordingBackend)
        if (actionSteps.isEmpty()) {
            return linkedMapOf(
                "manual_recording" to linkedMapOf(
                    "schema_version" to "oob.manual_recording.diagnostics.v1",
                    "recovered_after_restart" to false,
                    "action_source" to "none",
                    "completeness" to DONE_REASON_EMPTY_AFTER_RESTART,
                    "guarantees_no_missing_clicks" to false,
                    "a11_replay_actions_enabled" to false,
                    "replayable_action_count" to 0,
                    "recording_backend_counts" to emptyMap<String, Int>(),
                    "error_message" to EMPTY_RECORDING_ERROR
                )
            )
        }

        val completeSyntheticBackends = setOf(
            "overlay_touch",
            "overlay_touch_text_input",
            "ime_submit",
            "manual_control"
        )
        val syntheticComplete = backends.isNotEmpty() && backends.all { it in completeSyntheticBackends }
        val manualControlOnly = backends.isNotEmpty() && backends.all { it == "manual_control" }
        val actionSource = when {
            manualControlOnly -> "manual_control"
            backends.any { it.startsWith("device_getevent") || it == "raw_touch" } -> "mixed_real_touch"
            backends.any { it == "manual_control" } -> "mixed_manual_control"
            syntheticComplete -> "overlay_touch"
            else -> backends.firstOrNull() ?: "recovered_manual_recording"
        }
        return linkedMapOf(
            "manual_recording" to linkedMapOf(
                "schema_version" to "oob.manual_recording.diagnostics.v1",
                "recovered_after_restart" to true,
                "action_source" to actionSource,
                "completeness" to when {
                    manualControlOnly -> "complete_manual_control"
                    syntheticComplete -> "complete_overlay_touch"
                    else -> "recovered_incremental_actions"
                },
                "guarantees_no_missing_clicks" to syntheticComplete,
                "a11_replay_actions_enabled" to false,
                "replayable_action_count" to actionSteps.size,
                "recording_backend_counts" to backends.groupingBy { it }.eachCount()
            )
        )
    }

    private fun recordingBackend(step: Map<String, Any?>): String? {
        val metadata = step["metadata"] as? Map<*, *>
        return firstNonBlank(metadata?.get("recording_backend")).takeIf { it.isNotBlank() }
    }

    private fun firstNonBlank(vararg values: Any?): String {
        for (value in values) {
            val normalized = value?.toString()?.trim().orEmpty()
            if (normalized.isNotEmpty()) return normalized
        }
        return ""
    }
}
