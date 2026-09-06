package com.kaappi.studio.runtime

import android.content.Context
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.wasi.WasiOptions
import com.dylibso.chicory.wasi.WasiPreview1
import com.dylibso.chicory.wasm.Parser
import com.kaappi.studio.domain.RunResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class SchemeRunner(
    private val context: Context,
    private val moduleLoader: () -> ByteArray = {
        context.assets.open("kaappi.wasm").use { it.readBytes() }
    },
) {

    private var parsedModule: com.dylibso.chicory.wasm.WasmModule? = null

    private fun getModule(): com.dylibso.chicory.wasm.WasmModule {
        parsedModule?.let { return it }
        val module = Parser.parse(moduleLoader())
        parsedModule = module
        return module
    }

    suspend fun run(code: String): RunResult = withContext(Dispatchers.IO) {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val t0 = System.nanoTime()
        try {
            val workDir = File(context.cacheDir, "kaappi-run")
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
                store.instantiate("kaappi", getModule())
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                if (!msg.contains("exit code: 0") && !msg.contains("exit(0)")) {
                    stderr.write("$msg\n".toByteArray())
                }
            }

            programFile.delete()
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
}
