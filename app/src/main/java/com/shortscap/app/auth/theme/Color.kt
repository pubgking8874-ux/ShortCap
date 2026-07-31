package com.shortscap.app.auth.theme

import androidx.compose.ui.graphics.Color

/**
 * ShortsCap color tokens.
 *
 * NOTE: If your Dashboard module already defines a color system (e.g. in
 * com.shortscap.dashboard.theme.Color), DELETE this file and point
 * ShortsCapTheme.kt at that existing object instead — the goal is one
 * shared design system across Dashboard + Auth. These values are a
 * placeholder premium palette (indigo/violet primary, teal accent) so the
 * auth flow looks finished on its own until you wire it to the real one.
 */

// Brand
val BrandPrimary = Color(0xFF6C5CE7)      // Indigo-violet
val BrandPrimaryDark = Color(0xFF8C7CFF)
val BrandSecondary = Color(0xFF00C2A8)    // Teal accent
val BrandSecondaryDark = Color(0xFF2EE6CC)

// Light theme
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1EFFB)
val LightOnBackground = Color(0xFF1B1B23)
val LightOnSurface = Color(0xFF1B1B23)
val LightOnSurfaceVariant = Color(0xFF6B6B76)
val LightOutline = Color(0xFFE2E1EC)
val LightError = Color(0xFFE5484D)

// Dark theme
val DarkBackground = Color(0xFF121218)
val DarkSurface = Color(0xFF1B1B24)
val DarkSurfaceVariant = Color(0xFF26262F)
val DarkOnBackground = Color(0xFFF3F2F8)
val DarkOnSurface = Color(0xFFF3F2F8)
val DarkOnSurfaceVariant = Color(0xFFA6A5B3)
val DarkOutline = Color(0xFF35353F)
val DarkError = Color(0xFFFF6B6E)

// Gradients (used on Splash / Welcome hero)
val GradientStart = Color(0xFF6C5CE7)
val GradientEnd = Color(0xFF00C2A8)

// Semantic
val SuccessColor = Color(0xFF2ECC71)
val WarningColor = Color(0xFFFFC542)
