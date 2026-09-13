package com.rk.terminal.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedTerminalHostScriptTest {
    @Test
    fun finalProotProcessKeepsCallerStdio() {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val script = listOf(
            workingDirectory.resolve(
                "ReTerminal/core/main/src/main/assets/init-host.sh"
            ),
            workingDirectory.resolve("src/main/assets/init-host.sh")
        ).firstOrNull(File::isFile)?.readText()
            ?: error("Embedded terminal init-host.sh asset is missing.")

        assertTrue(
            script.contains(
                "exec \"\$LINKER\" \"\$PREFIX/local/bin/proot\" \$ARGS"
            )
        )
        assertFalse(script.contains("run_child \$LINKER \"\$PREFIX/local/bin/proot\""))
    }
}
