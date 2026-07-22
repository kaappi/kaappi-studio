package com.kaappi.studio.viewmodel

import androidx.lifecycle.ViewModel
import com.kaappi.studio.data.SettingsRepository
import com.kaappi.studio.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _themeMode = MutableStateFlow(settingsRepository.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _fontSize = MutableStateFlow(settingsRepository.getFontSize())
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
        _themeMode.value = mode
    }

    fun setFontSize(size: Int) {
        settingsRepository.setFontSize(size)
        _fontSize.value = size
    }
}
