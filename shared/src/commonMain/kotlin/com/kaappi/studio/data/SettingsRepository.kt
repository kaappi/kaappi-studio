package com.kaappi.studio.data

import com.kaappi.studio.domain.ThemeMode

/** Editor font size used when nothing (or something invalid) is stored. */
const val DEFAULT_FONT_SIZE = 14

/** Smallest font size the settings contract accepts; both platform UIs use the same range. */
const val MIN_FONT_SIZE = 10

/** Largest font size the settings contract accepts; both platform UIs use the same range. */
const val MAX_FONT_SIZE = 24

/**
 * Platform settings storage.
 *
 * # Contract (every actual must obey — issue #17)
 *
 * - **[getThemeMode][SettingsRepository.getThemeMode]** falls back to
 *   [ThemeMode.SYSTEM] when nothing is stored or the stored value is unknown
 *   (stale/corrupted) — it must never throw at startup.
 * - **Font size:** the valid range is [MIN_FONT_SIZE]..[MAX_FONT_SIZE].
 *   [setFontSize][SettingsRepository.setFontSize] clamps into that range
 *   before storing, and
 *   [getFontSize][SettingsRepository.getFontSize] returns the stored value
 *   only when it is in range, falling back to [DEFAULT_FONT_SIZE] otherwise —
 *   a stored 0 can never render invisible editor text.
 */
expect class SettingsRepository {
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun getFontSize(): Int
    fun setFontSize(size: Int)
    fun getLastOpenedFile(): String?
    fun setLastOpenedFile(path: String?)
}
