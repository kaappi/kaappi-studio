package com.kaappi.studio.data

import android.content.Context
import android.content.SharedPreferences
import com.kaappi.studio.domain.ThemeMode

actual class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("kaappi_studio_prefs", Context.MODE_PRIVATE)

    actual fun getThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        // Contract: a stale or corrupted stored value falls back to SYSTEM at
        // startup instead of crashing (matches the iOS actual).
        return try {
            ThemeMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    actual fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    actual fun getFontSize(): Int =
        // Contract: only in-range stored values are trusted; anything else
        // (including a stored 0) falls back to the default.
        prefs.getInt("font_size", DEFAULT_FONT_SIZE)
            .takeIf { it in MIN_FONT_SIZE..MAX_FONT_SIZE }
            ?: DEFAULT_FONT_SIZE

    actual fun setFontSize(size: Int) {
        // Contract: clamp into the valid range so nothing invalid is ever stored.
        prefs.edit().putInt("font_size", size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)).apply()
    }

    actual fun getLastOpenedFile(): String? = prefs.getString("last_opened_file", null)

    actual fun setLastOpenedFile(path: String?) {
        prefs.edit().putString("last_opened_file", path).apply()
    }
}
