package com.shortscap.app.util

import android.content.Context
import android.content.Intent

/**
 * Native Share App integration — launches the Android share sheet.
 *
 * DEV-PHASE: shares a static placeholder message. Later the share text will
 * be driven by a backend-provided Play Store URL / deep link; callers keep the
 * same one-liner [shareApp] so no UI change is required.
 */
object ShareUtils {
    fun shareApp(context: Context) {
        val shareText =
            "Take control of your digital wellbeing with ShortsCap."
        val chooserTitle = "Share ShortsCap via"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
    }
}