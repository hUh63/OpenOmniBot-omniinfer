package cn.com.omnimind.baselib.runlog

data class RunLogStepRecord(
    val step: Map<String, Any?>,
    val states: List<Map<String, Any?>>,
)

class RunLogWriter(
    initialStepIndex: Int = 0,
    private val sink: suspend (RunLogStepRecord) -> Unit,
) {
    var stepCount: Int = initialStepIndex
        private set

    suspend fun write(
        fact: Map<String, Any?>,
        states: List<Map<String, Any?>> = emptyList(),
    ): RunLogStepRecord {
        require("step_index" !in fact) { "run_log_fact_must_not_set_step_index" }
        val record = RunLogStepRecord(
            step = InternalRunLogStore.canonicalStep(
                linkedMapOf<String, Any?>("step_index" to stepCount).apply {
                    putAll(fact)
                },
            ),
            states = states,
        )
        sink(record)
        stepCount += 1
        return record
    }
}
