package com.nexa.social.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nexa.social.domain.model.ThemeConfig
import com.nexa.social.domain.model.ThemeMode

val LocalNexaThemeConfig = staticCompositionLocalOf { ThemeConfig() }

private fun darkScheme(accent: Color, secondary: Color, bg: BackgroundTokens) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = secondary,
    onSecondary = Color(0xFF04121A),
    tertiary = NexaPalette.PurpleLight,
    onTertiary = Color(0xFF1A0B33),
    background = bg.base,
    onBackground = NexaPalette.OnInk,
    surface = bg.surface,
    onSurface = NexaPalette.OnInk,
    surfaceVariant = bg.surfaceHigh,
    onSurfaceVariant = NexaPalette.OnInkMuted,
    outline = NexaPalette.InkOutline,
    outlineVariant = Color(0xFF1E2540),
    error = Color(0xFFFF6B7A),
    onError = Color.White
)

private fun lightScheme(accent: Color, secondary: Color, bg: BackgroundTokens) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = secondary,
    onSecondary = Color.White,
    tertiary = NexaPalette.Purple,
    onTertiary = Color.White,
    background = bg.base,
    onBackground = NexaPalette.OnPaper,
    surface = bg.surface,
    onSurface = NexaPalette.OnPaper,
    surfaceVariant = bg.surfaceHigh,
    onSurfaceVariant = NexaPalette.OnPaperMuted,
    outline = NexaPalette.PaperOutline,
    outlineVariant = Color(0xFFE2E5F1),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun NexaTheme(
    config: ThemeConfig = ThemeConfig(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val dark = when (config.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val bg = backgroundTokens(config.background, dark)
    val accent = config.accentColor()
    val secondary = config.secondaryColor()
    val colorScheme = if (dark) darkScheme(accent, secondary, bg) else lightScheme(accent, secondary, bg)

    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !dark && bg.base.luminance() > 0.5f
        }
    }

    val currentDensity = LocalDensity.current
    val accessibleDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * fontScale.coerceIn(0.85f, 1.30f)
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalNexaThemeConfig provides config,
        LocalDensity provides accessibleDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexaTypography,
            shapes = NexaShapes,
            content = content
        )
    }
}
