package com.ai.assistance.operit.terminal

data class CommandExecutionEvent(
    val commandId: String,
    val sessionId: String,
    val outputChunk: String,
    val isCompleted: Boolean
)

data class SessionDirectoryEvent(
    val sessionId: String,
    val currentDirectory: String
)
