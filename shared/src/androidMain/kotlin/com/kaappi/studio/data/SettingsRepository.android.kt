package com.kaappi.studio.data

import android.content.Context
import android.content.SharedPreferences
import com.kaappi.studio.domain.ThemeMode

actual class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("kaappi_studio_prefs", Context.MODE_PRIVATE)

    actual fun getThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return ThemeMode.valueOf(name)
    }

    actual fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    actual fun getFontSize(): Int = prefs.getInt("font_size", 14)

    actual fun setFontSize(size: Int) {
        prefs.edit().putInt("font_size", size).apply()
    }

    actual fun getLastOpenedFile(): String? = prefs.getString("last_opened_file", null)

    actual fun setLastOpenedFile(path: String?) {
        prefs.edit().putString("last_opened_file", path).apply()
    }
}
