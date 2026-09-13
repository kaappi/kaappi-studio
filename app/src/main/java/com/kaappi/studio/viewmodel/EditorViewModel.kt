package com.kaappi.studio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaappi.studio.domain.RunResult
import com.kaappi.studio.runtime.IsolatedRunSession
import com.kaappi.studio.runtime.SchemeExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorViewModel(
    private val runnerFactory: () -> SchemeExecutor,
) : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastResult = MutableStateFlow<RunResult?>(null)
    val lastResult: StateFlow<RunResult?> = _lastResult.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _currentFileName = MutableStateFlow<String?>(null)
    val currentFileName: StateFlow<String?> = _currentFileName.asStateFlow()

    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode.asStateFlow()

    private var lastDraft: String? = null

    private var runJob: Job? = null

    fun onReady() {
        _isReady.value = true
    }

    /**
     * Called when the editor WebView is torn down or recreated: the page the
     * previous readiness referred to no longer exists, so Play/Save must stay
     * disabled until the fresh page posts its `ready` event.
     */
    fun onWebViewReset() {
        _isReady.value = false
    }

    fun setPendingCode(code: String) {
        _pendingCode.value = code
    }

    /**
     * Stores the editor content pulled at `onPause`. Kept apart from
     * `pendingCode` on purpose: a draft must only be re-injected when the
     * WebView is actually recreated (by [consumeDraft] from the `ready`
     * callback), never on a plain pause/resume, which would reset the
     * editor's cursor, selection, and undo history.
     */
    fun saveDraft(code: String) {
        lastDraft = code
    }

    fun consumeDraft(): String? {
        val code = lastDraft
        lastDraft = null
        return code
    }

    fun consumePendingCode(): String? {
        val code = _pendingCode.value
        _pendingCode.value = null
        return code
    }

    fun runCode(code: String) {
        if (_isRunning.value) return
        _isRunning.value = true
        _lastResult.value = null

        // A fresh executor per run: combined with the runner's unique per-run
        // working directory, a stopped run can never interfere with a new one
        // (issue #9 / #11).
        val runner = runnerFactory()
        runJob = viewModelScope.launch {
            try {
                val result = runner.run(code)
                _lastResult.value = result
                _isRunning.value = false
            } finally {
                // A no-op after a normal result. When the job is cancelled —
                // by stopRun() or because the ViewModel was cleared — this
                // is what actually terminates the program.
                runner.stop()
            }
        }
    }

    /**
     * Terminates the current run and unblocks the UI immediately.
     *
     * Cancelling the run job reaches the executor's `stop()` through the
     * `finally` in [runCode]. In the app that executor is an
     * [com.kaappi.studio.runtime.IsolatedSchemeRunner], which kills the
     * `:runner` process the program is executing in, so a runaway program
     * really dies (issue #24). Any result the run might still deliver is
     * ignored: the job is cancelled and the stop notice below stands. Clearing
     * the ViewModel cancels `viewModelScope` and stops an in-flight run the
     * same way.
     */
    fun stopRun() {
        val job = runJob ?: return
        if (!_isRunning.value) return
        runJob = null
        job.cancel()
        _isRunning.value = false
        _lastResult.value = RunResult(
            stdout = "",
            stderr = IsolatedRunSession.STOPPED_MESSAGE,
            elapsedMs = 0.0,
        )
    }

    /**
     * Stores the open file's base name without the `.scm` extension; the title
     * and save-dialog prefill re-append it. Every caller already passes a base
     * name (`SchemeFile.name`, or the sanitized name returned by a save), so
     * nothing is stripped here.
     */
    fun setCurrentFile(name: String?) {
        _currentFileName.value = name
    }

    fun clearOutput() {
        _lastResult.value = null
    }
}
