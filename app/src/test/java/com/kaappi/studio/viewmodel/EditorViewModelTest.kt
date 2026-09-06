package com.kaappi.studio.viewmodel

import com.kaappi.studio.contextWithCacheDir
import com.kaappi.studio.runtime.SchemeRunner
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

    private fun awaitResult(vm: EditorViewModel): com.kaappi.studio.domain.RunResult {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.lastResult.value == null) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("runCode did not finish in time")
            Thread.sleep(10)
        }
        return vm.lastResult.value!!
    }

    @Test
    fun onReady_marksViewModelReady() {
        val vm = EditorViewModel(SchemeRunner(contextWithCacheDir(tmp.newFolder())) { emptyWasm })

        assertFalse(vm.isReady.value)
        vm.onReady()
        assertTrue(vm.isReady.value)
    }

    @Test
    fun pendingCode_isConsumedOnce() {
        val vm = EditorViewModel(SchemeRunner(contextWithCacheDir(tmp.newFolder())) { emptyWasm })

        vm.setPendingCode("(display 42)")
        assertEquals("(display 42)", vm.consumePendingCode())
        assertNull("pending code must be cleared after consumption", vm.consumePendingCode())
    }

    @Test
    fun setCurrentFile_updatesState() {
        val vm = EditorViewModel(SchemeRunner(contextWithCacheDir(tmp.newFolder())) { emptyWasm })

        vm.setCurrentFile("factorial")
        assertEquals("factorial", vm.currentFileName.value)
        vm.setCurrentFile(null)
        assertNull(vm.currentFileName.value)
    }

    @Test
    fun runCode_completesWithResultAndStopsRunningIndicator() = runBlocking {
        val vm = EditorViewModel(SchemeRunner(contextWithCacheDir(tmp.newFolder())) { emptyWasm })

        vm.runCode("(display \"hello\")")

        val result = awaitResult(vm)
        assertTrue("stderr was: ${result.stderr}", result.stderr.isEmpty())
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun runCode_surfacesRuntimeFailures() = runBlocking {
        val vm = EditorViewModel(SchemeRunner(contextWithCacheDir(tmp.newFolder())) {
            throw IllegalStateException("boom")
        })

        vm.runCode("(display 1)")

        val result = awaitResult(vm)
        assertTrue("stderr was: ${result.stderr}", result.stderr.contains("boom"))
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun clearOutput_dropsLastResult() = runBlocking {
        val vm = EditorViewModel(SchemeRunner(contextWithCacheDir(tmp.newFolder())) { emptyWasm })

        vm.runCode("(display 1)")
        awaitResult(vm)
        vm.clearOutput()

        assertNull(vm.lastResult.value)
    }
}
