package com.kaappi.studio.runtime

import android.os.Bundle
import com.kaappi.studio.domain.RunResult
import java.io.File
import java.util.UUID

/**
 * Messenger protocol between [IsolatedSchemeRunner] (app process) and
 * [SchemeRunnerService] (`:runner` process).
 *
 * Client → service: [MSG_RUN] with [KEY_CODE] in the data bundle and the
 * client's reply [android.os.Messenger] in `replyTo`.
 * Service → client: [MSG_STARTED] with the runner process id in `arg1`, then
 * [MSG_RESULT] once the program has finished.
 *
 * Program output is not sent inline: a Binder transaction is limited to about
 * 1 MB and a Scheme program can print far more than that. The service writes
 * stdout and stderr to files under the shared cache directory and the message
 * carries only their paths (plus the elapsed time); the client reads and
 * deletes them. Files that are never collected — a killed run, a crashed
 * client — are swept by [sweepStaleRunFiles] the next time a runner process
 * starts.
 */
internal object RunnerProtocol {
    const val MSG_RUN = 1
    const val MSG_STARTED = 2
    const val MSG_RESULT = 3

    const val KEY_CODE = "code"
    const val KEY_STDOUT_FILE = "stdoutFile"
    const val KEY_STDERR_FILE = "stderrFile"
    const val KEY_ELAPSED_MS = "elapsedMs"

    /** Set instead of the file keys when the service could not write the result files. */
    const val KEY_ERROR = "error"

    /** Shared with [SchemeRunner.newRunDirectory]: everything a run leaves behind lives here. */
    const val RUN_ROOT_DIR = "kaappi-run"

    fun encodeResult(result: RunResult, cacheDir: File): Bundle {
        val dir = File(File(cacheDir, RUN_ROOT_DIR), "result-${UUID.randomUUID()}").apply { mkdirs() }
        val stdoutFile = File(dir, "stdout").apply { writeText(result.stdout, Charsets.UTF_8) }
        val stderrFile = File(dir, "stderr").apply { writeText(result.stderr, Charsets.UTF_8) }
        return Bundle().apply {
            putString(KEY_STDOUT_FILE, stdoutFile.path)
            putString(KEY_STDERR_FILE, stderrFile.path)
            putDouble(KEY_ELAPSED_MS, result.elapsedMs)
        }
    }

    /**
     * The runner's fallback when the result files cannot be written (cache
     * directory full or unwritable): a short message always fits inline.
     */
    fun encodeFailure(message: String): Bundle = Bundle().apply {
        putString(KEY_ERROR, message)
    }

    /** Reads the result files named by [bundle] and removes them. */
    fun decodeResult(bundle: Bundle): RunResult {
        bundle.getString(KEY_ERROR)?.let { error ->
            return RunResult(stdout = "", stderr = error, elapsedMs = 0.0)
        }
        val stdoutFile = File(bundle.getString(KEY_STDOUT_FILE).orEmpty())
        val stderrFile = File(bundle.getString(KEY_STDERR_FILE).orEmpty())
        val result = RunResult(
            stdout = stdoutFile.readText(Charsets.UTF_8),
            stderr = stderrFile.readText(Charsets.UTF_8),
            elapsedMs = bundle.getDouble(KEY_ELAPSED_MS),
        )
        stdoutFile.parentFile?.deleteRecursively()
        return result
    }

    /**
     * Deletes everything under the run root. Called when a runner process
     * starts a service instance, at which point every `program.scm` here has
     * long been read (the interpreter loads it at startup) and every result
     * has been collected (the client reads its files before the next run can
     * begin). The one run that may still be executing is a program abandoned
     * by an app-process death, still going on its daemon thread in a cached
     * runner process; losing its working directory only affects a program
     * that writes files there, and nobody is listening for its output anyway.
     */
    fun sweepStaleRunFiles(cacheDir: File) {
        File(cacheDir, RUN_ROOT_DIR).listFiles()?.forEach { it.deleteRecursively() }
    }
}
