package cn.com.omnimind.bot.terminal

import com.rk.libcommons.ShellArgv
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellArgvTest {

    @Test
    fun buildShellScriptArgv_includesArgv0AndScriptPath() {
        assertArrayEquals(
            arrayOf("sh", "/tmp/init-host"),
            ShellArgv.buildShellScriptArgv("/tmp/init-host")
        )
    }

    @Test
    fun buildShellCommandArgv_includesArgv0AndDashC() {
        assertArrayEquals(
            arrayOf("sh", "-c", "echo ready"),
            ShellArgv.buildShellCommandArgv("echo ready")
        )
    }

    @Test
    fun buildShellScriptArgv_preservesAutoSetupOrder() {
        assertArrayEquals(
            arrayOf("sh", "/data/local/bin/init-host", "/bin/sh", "/data/local/bin/omni-setup.sh"),
            ShellArgv.buildShellScriptArgv(
                "/data/local/bin/init-host",
                "/bin/sh",
                "/data/local/bin/omni-setup.sh"
            )
        )
    }

    @Test
    fun formatExecSpec_rendersAllKeyFields() {
        val rendered = ShellArgv.formatExecSpec(
            shell = ShellArgv.SYSTEM_SH,
            args = arrayOf("sh", "/tmp/init-host"),
            workingDir = "/"
        )

        assertTrue(rendered.contains(ShellArgv.SYSTEM_SH))
        assertTrue(rendered.contains("/tmp/init-host"))
        assertTrue(rendered.contains("workingDir=\"/\""))
    }
}
