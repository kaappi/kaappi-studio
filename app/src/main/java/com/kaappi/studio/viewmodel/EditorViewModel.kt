package com.kaappi.studio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaappi.studio.domain.RunResult
import com.kaappi.studio.runtime.SchemeRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorViewModel(private val schemeRunner: SchemeRunner) : ViewModel() {

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

    fun onReady() {
        _isReady.value = true
    }

    fun setPendingCode(code: String) {
        _pendingCode.value = code
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

        viewModelScope.launch {
            val result = schemeRunner.run(code)
            _lastResult.value = result
            _isRunning.value = false
        }
    }

    fun setCurrentFile(name: String?) {
        _currentFileName.value = name
    }

    fun clearOutput() {
        _lastResult.value = null
    }
}
