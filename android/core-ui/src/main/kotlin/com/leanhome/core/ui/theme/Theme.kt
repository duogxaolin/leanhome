package com.leanhome.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = OnSurface,
    secondary = Secondary,
    onSecondary = OnPrimary,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = OnSurface,
    tertiary = Tertiary,
    onTertiary = OnPrimary,
    tertiaryContainer = TertiaryLight,
    onTertiaryContainer = OnSurface,
    error = Error,
    onError = OnPrimary,
    errorContainer = ErrorLight,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = OnBackgroundDark,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = OnBackgroundDark,
    secondary = SecondaryLight,
    onSecondary = OnBackgroundDark,
    secondaryContainer = Secondary,
    onSecondaryContainer = OnBackgroundDark,
    tertiary = TertiaryLight,
    onTertiary = OnBackgroundDark,
    tertiaryContainer = Tertiary,
    onTertiaryContainer = OnBackgroundDark,
    error = ErrorLight,
    onError = OnBackgroundDark,
    errorContainer = Error,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
)

@Composable
fun LeanHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
