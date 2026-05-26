package com.offpay.app.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NeoPopColorScheme = darkColorScheme(
    primary = NeoPopColors.Accent,
    onPrimary = NeoPopColors.Black,
    primaryContainer = NeoPopColors.AccentDim,
    onPrimaryContainer = NeoPopColors.Black,
    secondary = NeoPopColors.TextPrimary,
    onSecondary = NeoPopColors.Black,
    background = NeoPopColors.Black,
    onBackground = NeoPopColors.TextPrimary,
    surface = NeoPopColors.Surface,
    onSurface = NeoPopColors.TextPrimary,
    surfaceVariant = NeoPopColors.SurfaceHigh,
    onSurfaceVariant = NeoPopColors.TextSecondary,
    error = NeoPopColors.Danger,
    onError = NeoPopColors.TextPrimary,
    outline = NeoPopColors.Border,
    outlineVariant = NeoPopColors.BorderStrong
)

/**
 * Single dark theme — NeoPOP doesn't have a light mode by design. Anything
 * else would defeat the saturated-on-black contrast that makes the lime
 * accent pop.
 */
@Composable
fun OffPayTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = NeoPopColors.Black.toArgb()
            window.navigationBarColor = NeoPopColors.Black.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = NeoPopColorScheme,
        typography = NeoPopTypography,
        shapes = NeoPopShapes,
        content = content
    )
}
