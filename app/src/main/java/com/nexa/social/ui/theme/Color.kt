package com.nexa.social.ui.theme

import androidx.compose.ui.graphics.Color
import com.nexa.social.domain.model.BackgroundStyle
import com.nexa.social.domain.model.NexaAccent
import com.nexa.social.domain.model.ThemeConfig

/** Raw accent palette of the Nexa identity. */
object NexaPalette {
    val RoyalViolet = Color(0xFF7047FF)
    val Lavender = Color(0xFFB69CFF)
    val Midnight = Color(0xFF100B1C)
    val Cloud = Color(0xFFF8F5FF)

    val Purple = RoyalViolet
    val PurpleLight = Lavender
    val Blue = Color(0xFF3D7BFF)
    val BlueLight = Color(0xFF82B1FF)
    val Cyan = Color(0xFF00D0E0)
    val CyanLight = Color(0xFF80EFFF)
    val Green = Color(0xFF27C08A)
    val GreenLight = Color(0xFF7DE8BE)

    val Ink = Color(0xFF070912)
    val InkSurface = Color(0xFF0E1220)
    val InkSurfaceHigh = Color(0xFF161B2E)
    val InkOutline = Color(0xFF2A3350)
    val OnInk = Color(0xFFE8EBF7)
    val OnInkMuted = Color(0xFF9AA3C0)

    val Paper = Color(0xFFF5F6FB)
    val PaperSurface = Color(0xFFFFFFFF)
    val PaperSurfaceHigh = Color(0xFFECEEF8)
    val PaperOutline = Color(0xFFD4D8E8)
    val OnPaper = Color(0xFF14162B)
    val OnPaperMuted = Color(0xFF5B617A)
}

/** Returns the base background colors for a chosen background style and dark flag. */
data class BackgroundTokens(val base: Color, val surface: Color, val surfaceHigh: Color)

fun backgroundTokens(style: BackgroundStyle, dark: Boolean): BackgroundTokens {
    if (!dark) {
        val surface = when (style) {
            BackgroundStyle.DEEP_SPACE -> Color(0xFFF6F7FC)
            BackgroundStyle.GRAPHITE -> Color(0xFFF2F3F6)
            BackgroundStyle.MIDNIGHT -> NexaPalette.Cloud
            BackgroundStyle.OCEAN -> Color(0xFFF3F7FD)
        }
        return BackgroundTokens(
            base = surface,
            surface = Color.White,
            surfaceHigh = Color(0xFFEDE6FF)
        )
    }
    return when (style) {
        BackgroundStyle.DEEP_SPACE -> BackgroundTokens(
            base = Color(0xFF070912),
            surface = Color(0xFF0E1220),
            surfaceHigh = Color(0xFF161B2E)
        )
        BackgroundStyle.GRAPHITE -> BackgroundTokens(
            base = Color(0xFF0B0C0F),
            surface = Color(0xFF141519),
            surfaceHigh = Color(0xFF1D1F25)
        )
        BackgroundStyle.MIDNIGHT -> BackgroundTokens(
            base = NexaPalette.Midnight,
            surface = Color(0xFF1B152B),
            surfaceHigh = Color(0xFF261E3D)
        )
        BackgroundStyle.OCEAN -> BackgroundTokens(
            base = Color(0xFF050B16),
            surface = Color(0xFF0B1626),
            surfaceHigh = Color(0xFF142338)
        )
    }
}

/** Resolves the accent color from the current theme config. */
fun ThemeConfig.accentColor(): Color = when (accent) {
    NexaAccent.PURPLE -> NexaPalette.Purple
    NexaAccent.BLUE -> NexaPalette.Blue
    NexaAccent.CYAN -> NexaPalette.Cyan
    NexaAccent.GREEN -> NexaPalette.Green
    NexaAccent.CUSTOM -> Color(customAccentArgb)
}

fun ThemeConfig.secondaryColor(): Color = when (accent) {
    NexaAccent.PURPLE -> NexaPalette.Cyan
    NexaAccent.BLUE -> NexaPalette.Purple
    NexaAccent.CYAN -> NexaPalette.Blue
    NexaAccent.GREEN -> NexaPalette.Cyan
    NexaAccent.CUSTOM -> NexaPalette.Cyan
}

/** Accent options offered in the appearance screen. */
val accentChoices: List<Pair<NexaAccent, Color>> = listOf(
    NexaAccent.PURPLE to NexaPalette.Purple,
    NexaAccent.BLUE to NexaPalette.Blue,
    NexaAccent.CYAN to NexaPalette.Cyan,
    NexaAccent.GREEN to NexaPalette.Green
)
