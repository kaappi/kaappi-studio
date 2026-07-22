package com.kaappi.studio.data

import com.kaappi.studio.domain.ThemeMode
import platform.Foundation.NSUserDefaults

actual class SettingsRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getThemeMode(): ThemeMode {
        val name = defaults.stringForKey("theme_mode") ?: ThemeMode.SYSTEM.name
        return try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    actual fun setThemeMode(mode: ThemeMode) {
        defaults.setObject(mode.name, forKey = "theme_mode")
    }

    actual fun getFontSize(): Int =
        defaults.integerForKey("font_size").toInt().takeIf { it > 0 } ?: 14

    actual fun setFontSize(size: Int) {
        defaults.setInteger(size.toLong(), forKey = "font_size")
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
