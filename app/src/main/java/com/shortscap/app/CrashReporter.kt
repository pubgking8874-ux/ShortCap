package com.shortscap.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.PrintWriter
import java.io.StringWriter

/**
 * TEMPORARY diagnostic aid (remove once the Complete Profile crash is fixed).
 *
 * Installs an uncaught-exception handler that persists the crash stack trace,
 * then shows it on the NEXT app launch in an on-screen dialog so the exact
 * exception can be read/screenshotted without Logcat.
 */
object CrashReporter {

    private const val PREFS = "crash_reporter"
    private const val KEY_LAST_CRASH = "last_crash"

    fun install(context: Context) {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Best-effort save before the process dies (SharedPreferences is synchronous).
            runCatching {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, stackTraceString(throwable))
                    .apply()
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the saved crash trace (if any) and removes it from storage. */
    fun consumeLastCrash(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trace = prefs.getString(KEY_LAST_CRASH, null) ?: return null
        prefs.edit().remove(KEY_LAST_CRASH).apply()
        return trace
    }

    private fun stackTraceString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}

/** Full-screen-start dialog showing the saved crash trace with a copy button. */
@Composable
fun CrashReportDialog(trace: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "App crash detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Please screenshot this and send it to the developer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = trace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(trace))
                            onDismiss()
                        }
                    ) { Text("Copy & Close") }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}
