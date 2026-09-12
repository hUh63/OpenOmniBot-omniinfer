package com.ai.assistance.operit.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TerminalManagerTest {
    @Test
    fun `cancellation terminates a blocking hidden process`() = runBlocking {
        val process = BlockingProcess()
        val execution = async(Dispatchers.IO) {
            withCancellableHiddenExecProcess(process) {
                process.waitFor(30, TimeUnit.SECONDS)
            }
        }

        assertTrue(process.waitStarted.await(2, TimeUnit.SECONDS))
        execution.cancel()
        withTimeout(2_000L) { execution.cancelAndJoin() }

        assertTrue(process.destroyCalled.get())
        assertFalse(process.isAlive)
    }

    @Test
    fun `reader close interruption is treated as expected termination`() {
        assertTrue(
            isExpectedHiddenExecReaderTermination(
                InterruptedIOException("read interrupted by close() on another thread")
            )
        )
    }

    @Test
    fun `wrapped closed stream io exception is treated as expected termination`() {
        assertTrue(
            isExpectedHiddenExecReaderTermination(
                IllegalStateException("wrapper", IOException("stream closed"))
            )
        )
    }

    @Test
    fun `ordinary io exception is not treated as expected termination`() {
        assertFalse(
            isExpectedHiddenExecReaderTermination(
                IOException("permission denied")
            )
        )
    }

    private class BlockingProcess : Process() {
        val waitStarted = CountDownLatch(1)
        val destroyCalled = AtomicBoolean(false)
        private val exited = CountDownLatch(1)
        private val output = ByteArrayOutputStream()
        private val input = ByteArrayInputStream(ByteArray(0))
        private val error = ByteArrayInputStream(ByteArray(0))

        override fun getOutputStream(): OutputStream = output

        override fun getInputStream(): InputStream = input

        override fun getErrorStream(): InputStream = error

        override fun waitFor(): Int {
            waitStarted.countDown()
            exited.await()
            return 0
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            waitStarted.countDown()
            return exited.await(timeout, unit)
        }

        override fun exitValue(): Int {
            if (isAlive) throw IllegalThreadStateException("Process is still running")
            return 0
        }

        override fun destroy() {
            destroyCalled.set(true)
            exited.countDown()
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = exited.count > 0L
    }
}
