package cn.com.omnimind.bot.runlog

fun mapArg(value: Any?): Map<String, Any?> =
    when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().apply {
            value.forEach { (key, item) -> if (key != null) put(key.toString(), item) }
        }
        else -> emptyMap()
    }

fun listArg(value: Any?): List<Any?> =
    when (value) {
        is List<*> -> value
        is Array<*> -> value.toList()
        else -> emptyList()
    }

fun intArg(vararg values: Any?, defaultValue: Int): Int {
    values.forEach { value ->
        when (value) {
            is Number -> return value.toInt()
            is String -> value.trim().toIntOrNull()?.let { return it }
        }
    }
    return defaultValue
}

fun firstNonBlank(vararg values: Any?): String {
    values.forEach { value ->
        val text = value?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) return text
    }
    return ""
}
