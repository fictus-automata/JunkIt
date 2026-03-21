package com.junkphoto.cleaner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Green60,
    onPrimary = Green30,
    primaryContainer = Green40,
    onPrimaryContainer = Green80,
    secondary = Teal80,
    onSecondary = Teal40,
    tertiary = Amber80,
    onTertiary = Amber40,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = Error80,
    onError = Error40
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = SurfaceLight,
    primaryContainer = Green80,
    onPrimaryContainer = Green30,
    secondary = Teal40,
    tertiary = Amber40,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error = Error40
)

@Composable
fun JunkItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
