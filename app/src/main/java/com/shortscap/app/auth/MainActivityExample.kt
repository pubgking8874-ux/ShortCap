package com.shortscap.app.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shortscap.app.auth.navigation.AuthNavGraph
import com.shortscap.app.auth.theme.ShortsCapAuthTheme
import com.shortscap.app.auth.theme.ThemeMode

/**
 * EXAMPLE ONLY — shows how to host AuthNavGraph standalone so you can run
 * and preview all 7 screens today. In the real app this logic likely lives
 * in your existing top-level Activity/NavHost alongside the Dashboard
 * graph — copy the setContent block into wherever that already is, and
 * replace `onExitToDashboard` with your real navigation call.
 */
class MainActivityExample : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShortsCapAuthTheme(themeMode = ThemeMode.SYSTEM) {
                AuthNavGraph(
                    onExitToDashboard = {
                        // TODO: replace with real navigation into your
                        // Dashboard graph, e.g.:
                        // navController.navigate("dashboard") {
                        //     popUpTo("auth_graph") { inclusive = true }
                        // }
                    }
                )
            }
        }
    }
}
