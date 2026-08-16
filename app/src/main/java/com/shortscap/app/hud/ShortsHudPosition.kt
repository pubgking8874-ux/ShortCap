package com.shortscap.app.hud

/**
 * Pure position math for the draggable Shorts HUD overlay — kept free of
 * Android dependencies so it is directly unit-testable.
 *
 * The HUD stores its position as NORMALIZED (0..1) X/Y fractions of the
 * screen, so the saved position survives device size / orientation changes:
 * on show we convert fraction -> pixels, and on drag-end we clamp the pixel
 * position inside the screen and convert back to fractions.
 */
object ShortsHudPosition {

    /** Clamps a normalized (0..1) fraction into the valid range. */
    fun clampNormalized(value: Float): Float = value.coerceIn(0f, 1f)

    /** Clamps a pixel X inside the screen so the HUD never leaves it. */
    fun clampPixelX(x: Int, screenWidth: Int, hudWidth: Int): Int =
        x.coerceIn(0, (screenWidth - hudWidth).coerceAtLeast(0))

    /** Clamps a pixel Y inside the screen so the HUD never leaves it. */
    fun clampPixelY(y: Int, screenHeight: Int, hudHeight: Int): Int =
        y.coerceIn(0, (screenHeight - hudHeight).coerceAtLeast(0))

    /** Converts a pixel position into a normalized (0..1) fraction. */
    fun pixelToNormalized(pixel: Int, total: Int): Float =
        if (total <= 0) 0f else pixel.toFloat() / total
}
