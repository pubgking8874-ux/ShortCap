package com.shortscap.app.util

import android.content.Context
import android.content.Intent

/**
 * Native Share App integration — launches the Android share sheet.
 *
 * The message and chooser title come from the active language catalog, so the
 * share text follows the selected language. Later the message will be driven
 * by a backend-provided Play Store URL / deep link; callers keep the same
 * one-liner [shareApp] so no UI change is required.
 */
object ShareUtils {
    fun shareApp(context: Context, message: String, chooserTitle: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
    }
}