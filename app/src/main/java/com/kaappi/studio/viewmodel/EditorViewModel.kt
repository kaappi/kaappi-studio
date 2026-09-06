package com.kaappi.studio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaappi.studio.domain.RunResult
import com.kaappi.studio.runtime.SchemeRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorViewModel(
    private val runnerFactory: () -> SchemeRunner,
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

        // A fresh SchemeRunner per run: combined with the runner's unique
        // per-run working directory, an abandoned run (see stopRun) can never
        // interfere with a new one (issue #9 / #11).
        val runner = runnerFactory()
        runJob = viewModelScope.launch {
            val result = runner.run(code)
            _lastResult.value = result
            _isRunning.value = false
        }
    }

    /**
     * Stops tracking the current run and unblocks the UI immediately.
     *
     * Chicory has no cooperative cancellation, so there is no way to interrupt
     * the WASM: the abandoned job keeps running on its dedicated single-thread
     * executor until the program finishes on its own — for an infinite loop,
     * that means a thread and a CPU core stay busy indefinitely (actually
     * stopping the execution would require moving the runner into a separate
     * process). The abandoned run works in its own isolated working directory,
     * its output is discarded, and it never occupies the shared Dispatchers.IO
     * pool, so it cannot starve later runs or other app work. When the
     * surrounding ViewModel is cleared, viewModelScope is cancelled the same
     * way.
     */
    fun stopRun() {
        val job = runJob ?: return
        if (!_isRunning.value) return
        runJob = null
        job.cancel()
        _isRunning.value = false
        _lastResult.value = RunResult(
            stdout = "",
            stderr = "Run stopped by user.",
            elapsedMs = 0.0,
        )
    }

    fun setCurrentFile(name: String?) {
        _currentFileName.value = name
    }

    fun clearOutput() {
        _lastResult.value = null
    }
}
