package com.kaappi.studio.runtime

import com.kaappi.studio.domain.RunResult

/**
 * One execution of a Scheme program. An executor is single-use: it runs
 * exactly one program and is then discarded, so an abandoned run can never
 * interfere with a later one.
 *
 * Production runs go through [IsolatedSchemeRunner], which executes the
 * program in the dedicated `:runner` process so that [stop] can actually kill
 * a runaway program (issue #24). [SchemeRunner] is the in-process engine that
 * process hosts; on its own it cannot be stopped.
 */
interface SchemeExecutor {

    /** Runs [code] to completion and returns its captured output. */
    suspend fun run(code: String): RunResult

    /**
     * Terminates the run as far as the implementation is able to. Idempotent,
     * and a no-op once [run] has completed. Implementations must never throw
     * from here: it is called from the Stop button and from ViewModel teardown.
     */
    fun stop()
}
