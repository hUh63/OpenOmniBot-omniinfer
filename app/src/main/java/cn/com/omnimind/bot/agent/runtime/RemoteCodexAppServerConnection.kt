package cn.com.omnimind.bot.agent.runtime

internal interface RemoteCodexAppServerConnection {
    val isRunning: Boolean

    suspend fun start(
        onStdoutLine: suspend (String) -> Unit,
        onStderrLine: suspend (String) -> Unit,
        onExit: suspend (Int?) -> Unit
    )

    suspend fun writeLine(line: String)

    suspend fun close()
}
