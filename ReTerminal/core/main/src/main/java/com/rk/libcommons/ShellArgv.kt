package com.rk.libcommons

object ShellArgv {
    const val SYSTEM_SH: String = "/system/bin/sh"
    private const val DEFAULT_ARGV0: String = "sh"

    fun buildInteractiveShellArgv(argv0: String = DEFAULT_ARGV0): Array<String> {
        return arrayOf(argv0)
    }

    fun buildShellScriptArgv(
        scriptPath: String,
        vararg scriptArgs: String,
        argv0: String = DEFAULT_ARGV0
    ): Array<String> {
        require(scriptPath.isNotBlank()) { "scriptPath must not be blank." }
        return buildList {
            add(argv0)
            add(scriptPath)
            addAll(scriptArgs.asList())
        }.toTypedArray()
    }

    fun buildShellCommandArgv(
        command: String,
        argv0: String = DEFAULT_ARGV0
    ): Array<String> {
        require(command.isNotBlank()) { "command must not be blank." }
        return arrayOf(argv0, "-c", command)
    }

    fun formatExecSpec(shell: String, args: Array<String>, workingDir: String): String {
        return "shell=${quote(shell)} workingDir=${quote(workingDir)} argv=${
            args.joinToString(prefix = "[", postfix = "]") { quote(it) }
        }"
    }

    private fun quote(value: String): String {
        return buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }
}
