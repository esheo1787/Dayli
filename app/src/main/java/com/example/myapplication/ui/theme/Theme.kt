package com.example.myapplication.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.myapplication.ddaywidget.DdaySettings

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
    // 설정에서 테마 모드 읽기 (null이면 시스템 따라가기)
    themeMode: DdaySettings.ThemeMode? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()

    // 테마 모드 결정
    val effectiveThemeMode = themeMode ?: DdaySettings.getThemeModeEnum(context)
    val darkTheme = when (effectiveThemeMode) {
        DdaySettings.ThemeMode.SYSTEM -> systemDarkTheme
        DdaySettings.ThemeMode.LIGHT -> false
        DdaySettings.ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 상태바 색상 설정
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
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

/**
 * 다크 모드 여부 확인 (위젯 등에서 사용)
 */
fun isDarkMode(context: android.content.Context): Boolean {
    val themeMode = DdaySettings.getThemeModeEnum(context)
    return when (themeMode) {
        DdaySettings.ThemeMode.SYSTEM -> {
            val nightModeFlags = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        DdaySettings.ThemeMode.LIGHT -> false
        DdaySettings.ThemeMode.DARK -> true
    }
}
