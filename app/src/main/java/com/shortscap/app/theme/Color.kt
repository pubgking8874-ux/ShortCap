package com.shortscap.app.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware color scheme consumed by every ShortsCap composable via
 * [LocalScColors]. Components never hardcode theme colors — they read the
 * active palette, which swaps automatically when Dark / Light / System
 * Default is selected. Both palettes map 1:1 to the original RN tokens.
 */
data class ScColors(
    val Bg: Color,
    val Bg2: Color,
    val Card: Color,
    val CardHover: Color,
    val Divider: Color,
    val Accent: Color,
    val Accent2: Color,
    val Success: Color,
    val Warning: Color,
    val Danger: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextDisabled: Color,
    val SummaryCardGradientStart: Color,
    val SummaryCardGradientEnd: Color,
    val SummaryCardBorder: Color,
    val ProgressTrack: Color,
    val ChipActiveBg: Color,
    val ChipActiveText: Color,
    val DangerBtnBg: Color,
    val DangerBtnBorder: Color,
    val SwitchOffTrack: Color,
    val DrawerBg: Color,
    val ToastBg: Color,
    val ToastText: Color,
    val PieInstagram: Color,
    val PieYouTube: Color,
    val PieChrome: Color,
    val PieOther: Color,
)

/** Current Dark palette — identical to the original ShortsCap tokens. */
val ScDarkColors = ScColors(
    Bg = Color(0xFF0B0B0B),
    Bg2 = Color(0xFF111111),
    Card = Color(0xFF171717),
    CardHover = Color(0xFF1E1E1E),
    Divider = Color(0xFF252525),
    Accent = Color(0xFF3B82F6),
    Accent2 = Color(0xFF00C2FF),
    Success = Color(0xFF22C55E),
    Warning = Color(0xFFF59E0B),
    Danger = Color(0xFFEF4444),
    TextPrimary = Color(0xFFFFFFFF),
    TextSecondary = Color(0xFFB3B3B3),
    TextDisabled = Color(0xFF6E6E6E),
    SummaryCardGradientStart = Color(0xFF14202E),
    SummaryCardGradientEnd = Color(0xFF10161F),
    SummaryCardBorder = Color(0x403B82F6),
    ProgressTrack = Color(0xFF1C2733),
    ChipActiveBg = Color(0x263B82F6),
    ChipActiveText = Color(0xFFFFFFFF),
    DangerBtnBg = Color(0x1FEF4444),
    DangerBtnBorder = Color(0x4DEF4444),
    SwitchOffTrack = Color(0xFF2A2A2A),
    DrawerBg = Color(0xFF141414),
    ToastBg = Color(0xFF1C1C1C),
    ToastText = Color(0xFFFFFFFF),
    PieInstagram = Color(0xFF3B82F6),
    PieYouTube = Color(0xFF00C2FF),
    PieChrome = Color(0xFF22C55E),
    PieOther = Color(0xFF6E6E6E),
)

/** New Light palette — mirrors the Dark structure with light surfaces. */
val ScLightColors = ScColors(
    Bg = Color(0xFFF4F4F5),
    Bg2 = Color(0xFFECECEE),
    Card = Color(0xFFFFFFFF),
    CardHover = Color(0xFFF0F0F2),
    Divider = Color(0xFFE3E3E7),
    Accent = Color(0xFF3B82F6),
    Accent2 = Color(0xFF00C2FF),
    Success = Color(0xFF16A34A),
    Warning = Color(0xFFD97706),
    Danger = Color(0xFFDC2626),
    TextPrimary = Color(0xFF111111),
    TextSecondary = Color(0xFF52525B),
    TextDisabled = Color(0xFF9CA3AF),
    SummaryCardGradientStart = Color(0xFFE1EDFB),
    SummaryCardGradientEnd = Color(0xFFF3F6FA),
    SummaryCardBorder = Color(0x3D3B82F6),
    ProgressTrack = Color(0xFFD7E3F0),
    ChipActiveBg = Color(0x263B82F6),
    ChipActiveText = Color(0xFF1D4ED8),
    DangerBtnBg = Color(0x1FEF4444),
    DangerBtnBorder = Color(0x4DEF4444),
    SwitchOffTrack = Color(0xFFD4D4D8),
    DrawerBg = Color(0xFFFFFFFF),
    ToastBg = Color(0xFF2F2F33),
    ToastText = Color(0xFFFFFFFF),
    PieInstagram = Color(0xFF3B82F6),
    PieYouTube = Color(0xFF00C2FF),
    PieChrome = Color(0xFF16A34A),
    PieOther = Color(0xFF9CA3AF),
)

/** Provides the active palette to the whole app. */
val LocalScColors = staticCompositionLocalOf { ScDarkColors }

/** Brand app-icon accent colors — identical in both themes. */
val ScInstagram = Color(0xFFE1306C)
val ScChrome = Color(0xFF4285F4)
val ScWhatsApp = Color(0xFF25D366)

/** Radii used throughout (sc-root, sc-card, sc-chip, etc.) */
object ScShapes {
    const val RootRadius = 36
    const val CardRadius = 22
    const val ChipRadius = 999
    const val DrawerRadius = 28
    const val PopoverRadius = 16
    const val IconBtnRadius = 12
    const val SwitchRadius = 999
}
