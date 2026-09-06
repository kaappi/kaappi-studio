package com.kaappi.studio.data

import com.kaappi.studio.domain.ThemeMode
import platform.Foundation.NSUserDefaults

actual class SettingsRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getThemeMode(): ThemeMode {
        val name = defaults.stringForKey("theme_mode") ?: ThemeMode.SYSTEM.name
        // Contract: a stale or corrupted stored value falls back to SYSTEM
        // instead of throwing (matches the Android actual).
        return try {
            ThemeMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    actual fun setThemeMode(mode: ThemeMode) {
        defaults.setObject(mode.name, forKey = "theme_mode")
    }

    actual fun getFontSize(): Int =
        // Contract: only in-range stored values are trusted; anything else
        // (including a stored 0) falls back to the default.
        defaults.integerForKey("font_size").toInt()
            .takeIf { it in MIN_FONT_SIZE..MAX_FONT_SIZE }
            ?: DEFAULT_FONT_SIZE

    actual fun setFontSize(size: Int) {
        // Contract: clamp into the valid range so nothing invalid is ever stored.
        defaults.setInteger(size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE).toLong(), forKey = "font_size")
    }

    actual fun getLastOpenedFile(): String? = defaults.stringForKey("last_opened_file")

    actual fun setLastOpenedFile(path: String?) {
        if (path != null) {
            defaults.setObject(path, forKey = "last_opened_file")
        } else {
            defaults.removeObjectForKey("last_opened_file")
        }
    }
}
