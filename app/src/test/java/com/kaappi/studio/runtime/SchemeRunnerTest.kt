package com.kaappi.studio.runtime

import com.kaappi.studio.contextWithCacheDir
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SchemeRunnerTest {

    // Minimal valid WASM module (magic "\0asm" + version 1); the runner only needs
    // something Chicory can parse and instantiate.
    private val emptyWasm = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun run_returnsResultWithoutStderr_forInstantiableModule() {
        var loaderCalls = 0
        val runner = SchemeRunner(contextWithCacheDir(tmp.newFolder())) {
            loaderCalls++
            emptyWasm
        }

        val result = kotlinx.coroutines.runBlocking { runner.run("(display \"hi\")") }

        assertTrue("expected no stderr, got: ${result.stderr}", result.stderr.isEmpty())
        assertTrue(result.stdout.isEmpty())
        assertTrue(result.elapsedMs >= 0)
    }

    @Test
    fun run_cachesParsedModule_acrossRuns() {
        var loaderCalls = 0
        val runner = SchemeRunner(contextWithCacheDir(tmp.newFolder())) {
            loaderCalls++
            emptyWasm
        }

        kotlinx.coroutines.runBlocking {
            runner.run("(display 1)")
            runner.run("(display 2)")
        }

        assertTrue("module must be parsed once, was parsed $loaderCalls times", loaderCalls == 1)
    }

    @Test
    fun run_reportsLoaderFailure_asStderrInsteadOfThrowing() {
        val runner = SchemeRunner(contextWithCacheDir(tmp.newFolder())) {
            throw FileNotFoundException("kaappi.wasm missing")
        }

        val result = kotlinx.coroutines.runBlocking { runner.run("(display 1)") }

        assertTrue("stderr was: ${result.stderr}", result.stderr.contains("kaappi.wasm missing"))
    }

    @Test
    fun run_writesProgramToCacheDir_andCleansUp() {
        val cacheDir = tmp.newFolder()
        val runner = SchemeRunner(contextWithCacheDir(cacheDir)) { emptyWasm }

        kotlinx.coroutines.runBlocking { runner.run("(display 1)") }

        val workDir = File(cacheDir, "kaappi-run")
        assertTrue(workDir.isDirectory)
        val leftovers = workDir.listFiles()?.filter { it.name == "program.scm" } ?: emptyList()
        assertTrue("program.scm should be deleted after the run", leftovers.isEmpty())
    }
}
