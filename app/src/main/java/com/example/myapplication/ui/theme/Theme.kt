package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Soft Pastel - Light Color Scheme
private val LightColorScheme = lightColorScheme(
    primary = PastelBlueDark,
    onPrimary = Color.White,
    primaryContainer = PastelBlue,
    onPrimaryContainer = WarmGray,

    secondary = PastelPeachDark,
    onSecondary = Color.White,
    secondaryContainer = PastelPeach,
    onSecondaryContainer = WarmGray,

    tertiary = PastelMint,
    onTertiary = WarmGray,
    tertiaryContainer = PastelLavender,
    onTertiaryContainer = WarmGray,

    background = CreamWhite,
    onBackground = WarmGray,

    surface = SoftWhite,
    onSurface = WarmGray,
    surfaceVariant = PastelGrayLight,
    onSurfaceVariant = WarmGrayLight,

    outline = WarmGrayLight,
    outlineVariant = PastelGrayLight
)

// Soft Pastel - Dark Color Scheme
private val DarkColorScheme = darkColorScheme(
    primary = PastelBlueDarkMode,
    onPrimary = DarkBackground,
    primaryContainer = PastelBlueDark,
    onPrimaryContainer = LightGray,

    secondary = PastelPeachDarkMode,
    onSecondary = DarkBackground,
    secondaryContainer = PastelPeachDark,
    onSecondaryContainer = LightGray,

    tertiary = PastelMint,
    onTertiary = DarkBackground,
    tertiaryContainer = PastelLavender,
    onTertiaryContainer = LightGray,

    background = DarkBackground,
    onBackground = LightGray,

    surface = DarkSurface,
    onSurface = LightGray,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = LightGrayDim,

    outline = LightGrayDim,
    outlineVariant = DarkSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic Color 비활성화 (Soft Pastel 테마 사용)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 상태바 색상 설정
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
