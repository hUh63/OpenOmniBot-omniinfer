package cn.com.omnimind.bot.omniflow

internal class ExecutionRegistry {
    internal class Registration(
        val runId: String,
        val onStop: () -> Unit,
    ) {
        var stopped: Boolean = false
    }

    private val lock = Any()
    private var active: Registration? = null

    fun begin(runId: String, onStop: () -> Unit): Registration =
        synchronized(lock) {
            check(active == null) { "omniflow_execution_already_active" }
            Registration(runId, onStop).also { active = it }
        }

    fun stop(runOrTaskId: String? = null): Boolean {
        val normalizedId = runOrTaskId?.trim().orEmpty()
        val callback = synchronized(lock) {
            val execution = active ?: return false
            if (
                normalizedId.isNotEmpty() &&
                normalizedId != execution.runId &&
                !execution.runId.startsWith("$normalizedId-")
            ) {
                return false
            }
            if (execution.stopped) return false
            execution.stopped = true
            execution.onStop
        }
        callback()
        return true
    }

    fun end(registration: Registration) = synchronized(lock) {
        if (active === registration) active = null
    }
}
