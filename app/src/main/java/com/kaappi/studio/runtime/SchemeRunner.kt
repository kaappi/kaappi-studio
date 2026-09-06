package com.kaappi.studio.runtime

import android.content.Context
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.wasi.WasiOptions
import com.dylibso.chicory.wasi.WasiPreview1
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import com.kaappi.studio.domain.RunResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class SchemeRunner(
    private val context: Context,
    private val moduleCache: ModuleCache,
) {

    /**
     * Parses the WASM module once so it can be shared by several [SchemeRunner]
     * instances (each run uses its own runner, but re-parsing the binary for
     * every run would be wasteful). Parsing is lazy: nothing is read until the
     * first run actually needs the module.
     */
    class ModuleCache(moduleLoader: () -> ByteArray) {
        val module: WasmModule by lazy { Parser.parse(moduleLoader()) }
    }

    constructor(context: Context) : this(
        context,
        ModuleCache { context.assets.open("kaappi.wasm").use { it.readBytes() } },
    )

    /**
     * Dedicated single-thread executor for this run. Each SchemeRunner instance
     * is used for exactly one run, so a program abandoned via Stop keeps its own
     * thread until the WASM finishes instead of occupying the shared
     * Dispatchers.IO pool — later runs and app file I/O never queue behind a
     * runaway (issue #11). Threads are daemons and the executor is shut down in
     * [run]'s finally, so completed runs release their thread immediately.
     */
    private val runExecutor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "kaappi-scheme-run").apply { isDaemon = true }
        }
    private val runDispatcher = runExecutor.asCoroutineDispatcher()

    /**
     * Unique working directory per run so concurrent runs can never overwrite
     * each other's program.scm (issue #9). The directory is created by [run]
     * and removed when the run finishes, however long the (possibly abandoned)
     * execution takes.
     */
    internal fun newRunDirectory(): File =
        File(File(context.cacheDir, "kaappi-run"), "run-${UUID.randomUUID()}")

    suspend fun run(code: String): RunResult = try {
        withContext(runDispatcher) {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()

            val t0 = System.nanoTime()
            try {
                val workDir = newRunDirectory()
                workDir.mkdirs()
                val programFile = File(workDir, "program.scm")
                programFile.writeText(code, Charsets.UTF_8)

                val options = WasiOptions.builder()
                    .withStdout(stdout)
                    .withStderr(stderr)
                    .withStdin(ByteArrayInputStream(ByteArray(0)))
                    .withArguments(listOf("kaappi", "program.scm"))
                    .withDirectory(".", workDir.toPath())
                    .build()

                val wasi = WasiPreview1.builder()
                    .withOptions(options)
                    .build()

                val hostFunctions = wasi.toHostFunctions()
                val store = Store()
                for (fn in hostFunctions) {
                    store.addFunction(fn)
                }

                try {
                    store.instantiate("kaappi", moduleCache.module)
                } catch (e: Exception) {
                    val msg = e.message ?: e.toString()
                    if (!msg.contains("exit code: 0") && !msg.contains("exit(0)")) {
                        stderr.write("$msg\n".toByteArray())
                    }
                }

                workDir.deleteRecursively()
            } catch (e: Exception) {
                stderr.write("Runtime error: ${e.message}\n".toByteArray())
            }

            val elapsed = (System.nanoTime() - t0) / 1_000_000.0
            RunResult(
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = stderr.toString(Charsets.UTF_8.name()),
                elapsedMs = elapsed,
            )
        }
    } finally {
        // Completed runs release their thread right away; an abandoned run's
        // daemon thread lives until the WASM itself finishes (see stopRun).
        runExecutor.shutdown()
    }
}
