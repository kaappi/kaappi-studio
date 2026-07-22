package com.kaappi.studio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kaappi.studio.domain.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = DarkBrown,
    primaryContainer = TealDark,
    secondary = Amber80,
    onSecondary = DarkBrown,
    background = DarkBrown,
    onBackground = Cream,
    surface = MedBrown,
    onSurface = Cream,
    surfaceVariant = MedBrown,
    onSurfaceVariant = LightBrown,
    error = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = CreamDark,
    primaryContainer = Teal80,
    secondary = Amber40,
    onSecondary = CreamDark,
    background = CreamDark,
    onBackground = DarkBrown,
    surface = CreamDark,
    onSurface = DarkBrown,
    surfaceVariant = CreamDark,
    onSurfaceVariant = LightBrown,
    error = ErrorRedLight,
)

@Composable
fun KaappiStudioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
