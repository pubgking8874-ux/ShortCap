package com.shortscap.app.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The RN app was dark-only by default but exposed a Theme setting
 * (Dark / Light / System) under Settings > Appearance. We preserve that
 * same three-way switch here via ThemeMode, defaulting to Dark exactly
 * like the original `--bg:#0B0B0B` root token.
 *
 * Note on dynamic color (Material You): the ShortsCap design language uses
 * a fixed brand accent, so dynamic color is intentionally not applied here —
 * switching schemes keeps the visual identity intact while still adapting
 * every surface/text color to the active theme.
 */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/**
 * Resolves the active theme (SYSTEM follows the Android device automatically
 * and keeps updating live) and provides both the Material color scheme and
 * the ShortsCap [ScColors] palette to the whole composition.
 *
 * Theme switches animate the palette colors themselves (via
 * [animateColorAsState]) rather than recreating the composition tree, so the
 * transition is a smooth crossfade with no flicker and no loss of UI state
 * (scroll positions, pager pages, etc.).
 */
@Composable
fun ShortsCapTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val target = if (useDark) ScDarkColors else ScLightColors

    val colors = ScColors(
        Bg = target.Bg.animated(),
        Bg2 = target.Bg2.animated(),
        Card = target.Card.animated(),
        CardHover = target.CardHover.animated(),
        Divider = target.Divider.animated(),
        Accent = target.Accent.animated(),
        Accent2 = target.Accent2.animated(),
        Success = target.Success.animated(),
        Warning = target.Warning.animated(),
        Danger = target.Danger.animated(),
        TextPrimary = target.TextPrimary.animated(),
        TextSecondary = target.TextSecondary.animated(),
        TextDisabled = target.TextDisabled.animated(),
        SummaryCardGradientStart = target.SummaryCardGradientStart.animated(),
        SummaryCardGradientEnd = target.SummaryCardGradientEnd.animated(),
        SummaryCardBorder = target.SummaryCardBorder.animated(),
        ProgressTrack = target.ProgressTrack.animated(),
        StatIconBg = target.StatIconBg.animated(),
        ChipActiveBg = target.ChipActiveBg.animated(),
        ChipActiveText = target.ChipActiveText.animated(),
        DangerBtnBg = target.DangerBtnBg.animated(),
        DangerBtnBorder = target.DangerBtnBorder.animated(),
        SwitchOffTrack = target.SwitchOffTrack.animated(),
        DrawerBg = target.DrawerBg.animated(),
        ToastBg = target.ToastBg.animated(),
        ToastText = target.ToastText.animated(),
        PieInstagram = target.PieInstagram.animated(),
        PieYouTube = target.PieYouTube.animated(),
        PieChrome = target.PieChrome.animated(),
        PieOther = target.PieOther.animated(),
    )

    val colorScheme = if (useDark) {
        darkColorScheme(
            background = colors.Bg,
            surface = colors.Card,
            surfaceVariant = colors.CardHover,
            primary = colors.Accent,
            secondary = colors.Accent2,
            error = colors.Danger,
            onBackground = colors.TextPrimary,
            onSurface = colors.TextPrimary,
            outline = colors.Divider,
        )
    } else {
        lightColorScheme(
            background = colors.Bg,
            surface = colors.Card,
            surfaceVariant = colors.CardHover,
            primary = colors.Accent,
            secondary = colors.Accent2,
            error = colors.Danger,
            onBackground = colors.TextPrimary,
            onSurface = colors.TextPrimary,
            outline = colors.Divider,
        )
    }

    // Keep the Android system bars (status/nav icon colors) in sync with the
    // active app theme, updating reactively on every theme switch.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalScColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ScTypography,
            content = content,
        )
    }
}

/** Animates a single palette color toward its target over ~300ms. */
@Composable
private fun Color.animated(): Color =
    animateColorAsState(this, animationSpec = tween(300), label = "scThemeColor").value
