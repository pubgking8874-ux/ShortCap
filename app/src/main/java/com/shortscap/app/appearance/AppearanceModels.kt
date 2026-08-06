package com.shortscap.app.appearance

/**
 * Global text-size preference.
 *
 * [scale] is the multiplier applied to the platform font scale (via a
 * `LocalDensity` override at the app root), so every `sp` text size in the
 * entire application updates instantly — Dashboard, Settings, Profile,
 * Monitoring, Notifications, Permissions, Help & Support, About ShortsCap,
 * legal documents, the Web section and all future screens. Only typography
 * changes; layouts, icons, cards and spacing remain unchanged.
 *
 * [MEDIUM] is the default (scale 1.0). The enum maps 1:1 to a future backend
 * `GET /settings/appearance` entry.
 */
enum class TextSizeMode(val scale: Float) {
    SMALL(0.9f),
    MEDIUM(1.0f),
    LARGE(1.1f),
}
