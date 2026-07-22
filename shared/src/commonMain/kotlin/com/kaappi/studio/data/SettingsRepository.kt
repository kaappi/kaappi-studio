package com.kaappi.studio.data

import com.kaappi.studio.domain.ThemeMode

expect class SettingsRepository {
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun getFontSize(): Int
    fun setFontSize(size: Int)
    fun getLastOpenedFile(): String?
    fun setLastOpenedFile(path: String?)
}
