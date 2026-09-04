package cn.com.omnimind.bot.omniflow.ui

internal enum class ExecutionPhase(val label: String) {
    REASONING("智能推理"),
    AUTOMATIC("自动执行"),
}

internal fun initialExecutionPhase(usesModel: Boolean): ExecutionPhase =
    if (usesModel) ExecutionPhase.REASONING else ExecutionPhase.AUTOMATIC

internal class ExecutionStatusState(initialPhase: ExecutionPhase) {
    private var activePhase = initialPhase
    private var paused = false

    val label: String
        @Synchronized get() = if (paused) PAUSED_LABEL else activePhase.label

    @Synchronized
    fun updatePhase(phase: ExecutionPhase) {
        activePhase = phase
    }

    @Synchronized
    fun setPaused(value: Boolean) {
        paused = value
    }

    private companion object {
        const val PAUSED_LABEL = "已暂停，可手动操作"
    }
}
