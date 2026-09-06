package com.kaappi.studio.viewmodel

import com.kaappi.studio.contextWithCacheDir
import com.kaappi.studio.domain.RunResult
import com.kaappi.studio.runtime.SchemeRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val emptyWasm = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        // viewModelScope uses Dispatchers.Main, which does not exist on the JVM.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newRunnerFactory(): () -> SchemeRunner = {
        SchemeRunner(
            contextWithCacheDir(tmp.newFolder()),
            SchemeRunner.ModuleCache { emptyWasm },
        )
    }

    private fun awaitResult(vm: EditorViewModel): RunResult {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.lastResult.value == null) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("runCode did not finish in time")
            Thread.sleep(10)
        }
        return vm.lastResult.value!!
    }

    private fun awaitRunning(vm: EditorViewModel, expected: Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.isRunning.value != expected) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("isRunning did not become $expected in time")
            }
            Thread.sleep(10)
        }
    }

    @Test
    fun onReady_marksViewModelReady() {
        val vm = EditorViewModel(newRunnerFactory())

        assertFalse(vm.isReady.value)
        vm.onReady()
        assertTrue(vm.isReady.value)
    }

    @Test
    fun pendingCode_isConsumedOnce() {
        val vm = EditorViewModel(newRunnerFactory())

        vm.setPendingCode("(display 42)")
        assertEquals("(display 42)", vm.consumePendingCode())
        assertNull("pending code must be cleared after consumption", vm.consumePendingCode())
    }

    @Test
    fun setCurrentFile_updatesState() {
        val vm = EditorViewModel(newRunnerFactory())

        vm.setCurrentFile("factorial")
        assertEquals("factorial", vm.currentFileName.value)
        vm.setCurrentFile(null)
        assertNull(vm.currentFileName.value)
    }

    @Test
    fun runCode_completesWithResultAndStopsRunningIndicator() = runBlocking {
        val vm = EditorViewModel(newRunnerFactory())

        vm.runCode("(display \"hello\")")

        val result = awaitResult(vm)
        assertTrue("stderr was: ${result.stderr}", result.stderr.isEmpty())
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun runCode_buildsAFreshSchemeRunnerPerRun() = runBlocking {
        var runnerCreations = 0
        val vm = EditorViewModel {
            runnerCreations++
            SchemeRunner(
                contextWithCacheDir(tmp.newFolder()),
                SchemeRunner.ModuleCache { emptyWasm },
            )
        }

        vm.runCode("(display 1)")
        awaitResult(vm)
        vm.runCode("(display 2)")
        awaitResult(vm)

        assertEquals("each run must use its own SchemeRunner instance", 2, runnerCreations)
    }

    @Test
    fun runCode_surfacesRuntimeFailures() = runBlocking {
        val vm = EditorViewModel {
            SchemeRunner(
                contextWithCacheDir(tmp.newFolder()),
                SchemeRunner.ModuleCache { throw IllegalStateException("boom") },
            )
        }

        vm.runCode("(display 1)")

        val result = awaitResult(vm)
        assertTrue("stderr was: ${result.stderr}", result.stderr.contains("boom"))
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun clearOutput_dropsLastResult() = runBlocking {
        val vm = EditorViewModel(newRunnerFactory())

        vm.runCode("(display 1)")
        awaitResult(vm)
        vm.clearOutput()

        assertNull(vm.lastResult.value)
    }

    @Test
    fun stopRun_unblocksUiWhileTheProgramIsStillExecuting() {
        // The module loader blocks until we release the gate, simulating a
        // long-running WASM execution that cannot be cooperatively cancelled.
        val gate = CountDownLatch(1)
        val cacheDir = tmp.newFolder()
        val vm = EditorViewModel {
            SchemeRunner(
                contextWithCacheDir(cacheDir),
                SchemeRunner.ModuleCache { gate.await(5, TimeUnit.SECONDS); emptyWasm },
            )
        }

        vm.runCode("(define (f) (f)) (f)")
        awaitRunning(vm, expected = true)

        vm.stopRun()

        assertFalse("Stop must release the UI immediately", vm.isRunning.value)
        val result = vm.lastResult.value
        assertTrue(
            "Stop must surface a stop notice, was: $result",
            result?.stderr?.contains("stopped") == true,
        )

        // Release the abandoned execution; it must not overwrite the stop
        // notice when it eventually finishes.
        gate.countDown()
        awaitAbandonedRunCleanup(cacheDir)
        assertTrue("stop notice must survive the abandoned run", vm.lastResult.value == result)
        assertFalse(vm.isRunning.value)
    }

    /** Waits until the abandoned run has removed its per-run work directory. */
    private fun awaitAbandonedRunCleanup(cacheDir: File) {
        val runRoot = File(cacheDir, "kaappi-run")
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (runRoot.listFiles()?.isEmpty() ?: true) return
            Thread.sleep(10)
        }
        throw AssertionError("abandoned run did not clean up its work directory in time")
    }

    @Test
    fun stopRun_isANoOpWhenNothingIsRunning() {
        val vm = EditorViewModel(newRunnerFactory())

        vm.stopRun()

        assertFalse(vm.isRunning.value)
        assertNull(vm.lastResult.value)
    }

    @Test
    fun runCode_ignoresRequestsWhileARunIsAlreadyExecuting() {
        val gate = CountDownLatch(1)
        val vm = EditorViewModel {
            SchemeRunner(
                contextWithCacheDir(tmp.newFolder()),
                SchemeRunner.ModuleCache { gate.await(5, TimeUnit.SECONDS); emptyWasm },
            )
        }

        vm.runCode("(display 1)")
        awaitRunning(vm, expected = true)
        vm.runCode("(display 2)")

        // Second request is ignored; releasing the gate lets the first run finish.
        gate.countDown()
        val result = awaitResult(vm)
        assertFalse(vm.isRunning.value)
        assertTrue(result.stderr.isEmpty())
    }
}
