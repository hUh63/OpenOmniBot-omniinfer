package cn.com.omnimind.baselib.runlog

data class Action(
    val tool: String,
    val args: Map<String, Any?>,
) {
    init {
        require(OobActionSchema.canonicalToolName(tool) == tool) { "canonical_action_tool_invalid:$tool" }
    }

    fun asMap(): Map<String, Any?> = linkedMapOf(
        OobActionSchema.ROOT_TOOL to tool,
        OobActionSchema.ROOT_ARGS to args,
    )

    companion object {
        fun fromMap(value: Map<String, Any?>): Action {
            require(value.keys == setOf(OobActionSchema.ROOT_TOOL, OobActionSchema.ROOT_ARGS)) {
                "canonical_action_fields_invalid"
            }
            val tool = value[OobActionSchema.ROOT_TOOL]?.toString()?.trim().orEmpty()
            val rawArgs = value[OobActionSchema.ROOT_ARGS]
            require(rawArgs is Map<*, *>) { "canonical_action_args_invalid" }
            val args = rawArgs.entries.associateTo(linkedMapOf()) { (key, item) ->
                require(key is String) { "canonical_action_arg_key_invalid" }
                key to item
            }
            return actionOf(tool, args)
        }
    }
}

fun actionOf(tool: String, args: Map<String, Any?> = emptyMap()): Action =
    Action(
        tool = OobActionSchema.canonicalToolName(tool)
            ?: error("canonical_action_tool_invalid:$tool"),
        args = LinkedHashMap(args),
    )
