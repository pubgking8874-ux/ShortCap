package com.shortscap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TEMPORARY diagnostic — remove once the Complete Profile crash is fixed.
        CrashReporter.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            val crashTrace = remember { CrashReporter.consumeLastCrash(applicationContext) }
            AppRootNavHost()
            if (crashTrace != null) {
                CrashReportDialog(trace = crashTrace, onDismiss = {})
            }
        }
    }
}
