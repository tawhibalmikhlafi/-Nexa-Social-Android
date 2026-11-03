package com.nexa.social.domain.model

data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.DARK,
    val accent: NexaAccent = NexaAccent.PURPLE,
    val customAccentArgb: Long = 0xFF7C4DFF,
    val background: BackgroundStyle = BackgroundStyle.DEEP_SPACE,
    val useDynamicColor: Boolean = false
)

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class NexaAccent {
    PURPLE,
    BLUE,
    CYAN,
    GREEN,
    CUSTOM
}

enum class BackgroundStyle {
    DEEP_SPACE,
    GRAPHITE,
    MIDNIGHT,
    OCEAN
}
