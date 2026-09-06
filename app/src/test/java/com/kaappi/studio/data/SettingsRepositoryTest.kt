package com.kaappi.studio.data

import android.content.Context
import android.content.SharedPreferences
import com.kaappi.studio.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class SettingsRepositoryTest {

    private fun mockContext(prefs: SharedPreferences): Context {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(
            context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()),
        ).thenReturn(prefs)
        return context
    }

    private fun mockPrefs(storedTheme: String?, storedFontSize: Int?): SharedPreferences {
        val prefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(
            prefs.getString(Mockito.eq("theme_mode"), Mockito.any()),
        ).thenReturn(storedTheme)
        Mockito.`when`(
            prefs.getInt(Mockito.eq("font_size"), Mockito.anyInt()),
        ).thenAnswer { invocation ->
            storedFontSize ?: invocation.getArgument<Int>(1)
        }
        return prefs
    }

    private fun repository(storedTheme: String?, storedFontSize: Int? = null): SettingsRepository =
        SettingsRepository(mockContext(mockPrefs(storedTheme, storedFontSize)))

    // --- Theme contract: unknown/corrupted stored values fall back to SYSTEM.

    @Test
    fun getThemeMode_returnsTheStoredValue() {
        assertEquals(ThemeMode.DARK, repository("DARK").getThemeMode())
    }

    @Test
    fun getThemeMode_fallsBackToSystemWhenNothingIsStored() {
        assertEquals(ThemeMode.SYSTEM, repository(null).getThemeMode())
    }

    @Test
    fun getThemeMode_fallsBackToSystemOnUnknownStoredValue() {
        assertEquals(ThemeMode.SYSTEM, repository("bogus").getThemeMode())
        assertEquals(ThemeMode.SYSTEM, repository("").getThemeMode())
    }

    // --- getFontSize contract: only in-range stored values are trusted.

    @Test
    fun getFontSize_returnsTheStoredValueWhenInRange() {
        assertEquals(18, repository(null, storedFontSize = 18).getFontSize())
    }

    @Test
    fun getFontSize_fallsBackToDefaultForStoredZero() {
        assertEquals(DEFAULT_FONT_SIZE, repository(null, storedFontSize = 0).getFontSize())
    }

    @Test
    fun getFontSize_fallsBackToDefaultForOutOfRangeStoredValues() {
        assertEquals(DEFAULT_FONT_SIZE, repository(null, storedFontSize = 5).getFontSize())
        assertEquals(DEFAULT_FONT_SIZE, repository(null, storedFontSize = 999).getFontSize())
        assertEquals(DEFAULT_FONT_SIZE, repository(null, storedFontSize = -3).getFontSize())
    }

    @Test
    fun getFontSize_returnsTheDefaultWhenNothingIsStored() {
        assertEquals(DEFAULT_FONT_SIZE, repository(null, storedFontSize = null).getFontSize())
    }

    // --- setFontSize contract: clamps into the valid range before storing.

    @Test
    fun setFontSize_clampsIntoTheValidRange() {
        val prefs = mockPrefs(storedTheme = null, storedFontSize = 14)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(prefs.edit()).thenReturn(editor)
        Mockito.`when`(
            editor.putInt(Mockito.anyString(), Mockito.anyInt()),
        ).thenReturn(editor)
        val repo = SettingsRepository(mockContext(prefs))

        repo.setFontSize(0)
        repo.setFontSize(5)
        repo.setFontSize(16)
        repo.setFontSize(999)

        Mockito.verify(editor, Mockito.times(2))
            .putInt(Mockito.eq("font_size"), Mockito.eq(MIN_FONT_SIZE))
        Mockito.verify(editor).putInt(Mockito.eq("font_size"), Mockito.eq(16))
        Mockito.verify(editor).putInt(Mockito.eq("font_size"), Mockito.eq(MAX_FONT_SIZE))
        Mockito.verify(editor, Mockito.times(4)).apply()
    }
}
