package com.kaappi.studio.runtime

import com.kaappi.studio.domain.RunResult
import kotlinx.coroutines.CompletableDeferred

/**
 * Transport-independent state of one run in the `:runner` process. It owns
 * the ordering rules that make Stop reliable, so they can be unit tested
 * without Binder:
 *
 * - The runner process reports its pid ([onStarted]) as soon as it accepts the
 *   program. A [stop] before that pid is known is remembered and the process is
 *   killed the moment the pid arrives, so pressing Stop in the few milliseconds
 *   between Run and the ack still terminates the program.
 * - A result or process death after [stop] is ignored; the stop verdict stands.
 * - [stop] after a normal result kills nothing: the process is idle and may be
 *   reused by the next run.
 *
 * [killProcess] is invoked at most once, and only while the run is unfinished.
 */
internal class IsolatedRunSession(private val killProcess: (pid: Int) -> Unit) {

    private enum class State { RUNNING, FINISHED, STOPPED }

    private val lock = Any()
    private val result = CompletableDeferred<RunResult>()
    private var state = State.RUNNING
    private var pid: Int? = null
    private var killed = false

    /** True once a result, a failure or a stop has been recorded. */
    val isFinished: Boolean
        get() = synchronized(lock) { state != State.RUNNING }

    /** The runner process accepted the program and is executing it. */
    fun onStarted(pid: Int) {
        val killNow = synchronized(lock) {
            this.pid = pid
            takeKill()
        }
        if (killNow) killProcess(pid)
    }

    /** The runner process finished the program normally. */
    fun onResult(runResult: RunResult) {
        finish(runResult)
    }

    /** The runner process went away before delivering a result. */
    fun onProcessDied() {
        finish(RunResult(stdout = "", stderr = PROCESS_DIED_MESSAGE, elapsedMs = 0.0))
    }

    /** The runner process could not be started at all. */
    fun onStartFailed(reason: String) {
        finish(RunResult(stdout = "", stderr = reason, elapsedMs = 0.0))
    }

    /**
     * Records the stop and kills the runner process if its pid is already
     * known (otherwise [onStarted] kills it later). [await] returns the stop
     * notice from now on.
     */
    fun stop() {
        val target = synchronized(lock) {
            if (state != State.RUNNING) return
            state = State.STOPPED
            result.complete(RunResult(stdout = "", stderr = STOPPED_MESSAGE, elapsedMs = 0.0))
            pid.takeIf { takeKill() }
        }
        target?.let(killProcess)
    }

    suspend fun await(): RunResult = result.await()

    private fun finish(runResult: RunResult) {
        synchronized(lock) {
            if (state != State.RUNNING) return
            state = State.FINISHED
            result.complete(runResult)
        }
    }

    /** Must hold [lock]. True exactly once: when stopped, pid known, not yet killed. */
    private fun takeKill(): Boolean {
        if (state != State.STOPPED || pid == null || killed) return false
        killed = true
        return true
    }

    companion object {
        const val STOPPED_MESSAGE = "Run stopped by user."
        const val PROCESS_DIED_MESSAGE =
            "The Scheme runner process exited unexpectedly (it may have run out of memory)."
    }
}
