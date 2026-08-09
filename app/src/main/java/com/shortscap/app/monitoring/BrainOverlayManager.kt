package com.shortscap.app.monitoring

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.shortscap.app.R

/**
 * The short-video platforms the Brain overlay may appear above. Package-name
 * based and data-driven (mirrors the platforms in MonitoringSettings) so new
 * platforms can be added without touching the overlay code.
 */
val SupportedShortVideoPackages = setOf(
    "com.google.android.youtube",   // YouTube Shorts
    "com.instagram.android",        // Instagram Reels
    "com.facebook.katana",          // Facebook Reels
    "com.snapchat.android",         // Snapchat Spotlight
    "com.zhiliaoapp.musically",     // TikTok
    "com.ss.android.ugc.aweme",     // TikTok (secondary)
)

/**
 * BrainOverlayManager — the small, unobtrusive monitoring Brain indicator.
 *
 * It appears ONLY while the user is inside a supported short-video app (the
 * Accessibility Service drives show/hide from the foreground-package events),
 * and ONLY when the user granted the "Display Over Other Apps" permission.
 * It never shows globally — never over Settings, ShortsCap itself, or any
 * other unrelated app — and it is not touchable, so it never intercepts
 * touches. All WindowManager calls are guarded so a transient window failure
 * can never crash the service.
 */
object BrainOverlayManager {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    /** Shows the Brain indicator (no-op unless overlay permission granted). */
    fun show(context: Context) {
        if (overlayView != null) return
        // Guard: the "Display Over Other Apps" permission is the only gate.
        if (!Settings.canDrawOverlays(context)) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val view = ImageView(context).apply {
            setImageResource(R.drawable.ic_brain)
            setBackgroundResource(R.drawable.brain_overlay_bg)
            contentDescription = context.getString(R.string.brain_overlay_label)
        }
        val density = context.resources.displayMetrics.density
        val size = (46 * density).toInt()
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Top-center, clear of the status bar.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (80 * density).toInt()
        }

        runCatching { wm.addView(view, params) }
            .onSuccess {
                overlayView = view
                windowManager = wm
            }
    }

    /** Removes the Brain indicator if it is on screen. */
    fun hide() {
        val view = overlayView ?: return
        val wm = windowManager
        overlayView = null
        windowManager = null
        if (wm != null) runCatching { wm.removeView(view) }
    }
}
