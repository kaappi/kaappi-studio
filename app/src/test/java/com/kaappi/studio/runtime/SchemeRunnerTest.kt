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

    private fun runner(cacheDir: File, loaderCalls: IntArray? = null): SchemeRunner =
        SchemeRunner(
            contextWithCacheDir(cacheDir),
            SchemeRunner.ModuleCache {
                loaderCalls?.let { it[0]++ }
                emptyWasm
            },
        )

    @Test
    fun run_returnsResultWithoutStderr_forInstantiableModule() {
        val runner = runner(tmp.newFolder())

        val result = kotlinx.coroutines.runBlocking { runner.run("(display \"hi\")") }

        assertTrue("expected no stderr, got: ${result.stderr}", result.stderr.isEmpty())
        assertTrue(result.stdout.isEmpty())
        assertTrue(result.elapsedMs >= 0)
    }

    @Test
    fun moduleCache_parsesModuleOnlyOnce() {
        val loaderCalls = intArrayOf(0)
        val cache = SchemeRunner.ModuleCache {
            loaderCalls[0]++
            emptyWasm
        }

        cache.module
        cache.module

        assertTrue("module must be parsed once, was parsed ${loaderCalls[0]} times", loaderCalls[0] == 1)
    }

    @Test
    fun run_reportsLoaderFailure_asStderrInsteadOfThrowing() {
        val runner = SchemeRunner(
            contextWithCacheDir(tmp.newFolder()),
            SchemeRunner.ModuleCache { throw FileNotFoundException("kaappi.wasm missing") },
        )

        val result = kotlinx.coroutines.runBlocking { runner.run("(display 1)") }

        assertTrue("stderr was: ${result.stderr}", result.stderr.contains("kaappi.wasm missing"))
    }

    @Test
    fun run_writesProgramToUniqueWorkDir_andCleansUp() {
        val cacheDir = tmp.newFolder()
        val runner = runner(cacheDir)

        kotlinx.coroutines.runBlocking { runner.run("(display 1)") }

        val runRoot = File(cacheDir, "kaappi-run")
        assertTrue(runRoot.isDirectory)
        val leftovers = runRoot.listFiles().orEmpty()
        assertTrue(
            "per-run work directories must be removed after the run, found: $leftovers",
            leftovers.isEmpty(),
        )
    }

    @Test
    fun runDirectory_isUniquePerRun_soConcurrentRunsCannotRace() {
        val runner = runner(tmp.newFolder())

        val dirs = (1..10).map { runner.newRunDirectory() }

        assertTrue("run directories must be unique, got: $dirs", dirs.size == dirs.toSet().size)
    }
}
