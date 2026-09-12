package cn.com.omnimind.bot.manager

internal fun buildManualRecordingFinalizedPayload(
    recordingSuccess: Boolean,
    phase: String,
    diagnostics: Map<String, Any?>,
    recordingErrorMessage: String?,
    runLog: Map<String, Any?>,
    conversion: Map<String, Any?>?,
): Map<String, Any?> {
    val conversionSuccess = conversion?.get("success") == true
    return linkedMapOf<String, Any?>(
        "success" to recordingSuccess,
        "phase" to phase,
        "diagnostics" to diagnostics.takeIf { it.isNotEmpty() },
        "error_code" to if (recordingSuccess) null else "HUMAN_TRAJECTORY_RECORDING_FAILED",
        "error_message" to recordingErrorMessage?.takeIf { it.isNotBlank() },
        "run_log" to runLog,
        "function" to conversion?.get("function").takeIf { conversionSuccess },
        "function_error" to conversion?.let { value ->
            if (conversionSuccess) null else linkedMapOf(
                "code" to value["error_code"],
                "message" to value["error_message"],
            ).filterValues { it != null }
        },
    ).filterValues { it != null }
}
