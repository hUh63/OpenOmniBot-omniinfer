package com.ai.assistance.operit.terminal.provider.type

data class HiddenExecResult(
    val output: String,
    val exitCode: Int,
    val state: State = State.OK,
    val error: String = "",
    val rawOutputPreview: String = ""
) {
    enum class State {
        OK,
        SHELL_START_FAILED,
        SHELL_NOT_READY,
        PROCESS_EXITED,
        MISSING_BEGIN_MARKER,
        MISSING_END_MARKER,
        INVALID_EXIT_CODE,
        TIMEOUT,
        EXECUTION_ERROR
    }

    val isOk: Boolean
        get() = state == State.OK
}

enum class TerminalType {
    LOCAL,
    SSH,
    ADB
}
