package com.ai.assistance.operit.terminal.data

import com.ai.assistance.operit.terminal.provider.type.TerminalType
import com.termux.terminal.TerminalSession
import java.util.UUID

data class QueuedCommand(
    val id: String,
    val command: String
)

class CommandHistoryItem(
    val id: String,
    prompt: String,
    command: String,
    output: String,
    isExecuting: Boolean = false
) {
    private var _prompt: String = prompt
    private var _command: String = command
    private var _output: String = output
    private var _isExecuting: Boolean = isExecuting

    val prompt: String get() = _prompt
    val command: String get() = _command
    val output: String get() = _output
    val isExecuting: Boolean get() = _isExecuting

    fun setPrompt(value: String) { _prompt = value }
    fun setCommand(value: String) { _command = value }
    fun setOutput(value: String) { _output = value }
    fun setExecuting(value: Boolean) { _isExecuting = value }
}

enum class SessionInitState {
    INITIALIZING,
    READY
}

data class TerminalSessionData(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val terminalType: TerminalType = TerminalType.LOCAL,
    val terminalSession: TerminalSession? = null,
    val currentDirectory: String = "/root",
    val initState: SessionInitState = SessionInitState.INITIALIZING,
    val transcript: String = "",
    val currentExecutingCommand: CommandHistoryItem? = null,
    val scrollOffsetY: Float = 0f
) {
    val isInitializing: Boolean
        get() = initState != SessionInitState.READY
}

data class TerminalState(
    val sessions: List<TerminalSessionData> = emptyList(),
    val currentSessionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentSession: TerminalSessionData?
        get() = currentSessionId?.let { sessionId ->
            sessions.find { it.id == sessionId }
        }
}
