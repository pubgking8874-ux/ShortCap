package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors

/**
 * Blocked Apps — UI only today. Future: list installed applications here and
 * let the user pick which apps to block (GET / UPDATE Blocked Apps backend
 * APIs, or Android package manager + Usage Stats data).
 */
@Composable
fun BlockedAppsScreen(onBack: () -> Unit) {
    val colors = LocalScColors.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = "Blocked Apps", onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            ScEmptyState(
                icon = Icons.Filled.DoNotDisturbOn,
                title = "No blocked apps yet",
                subtitle = "Installed apps will appear here so you can choose " +
                    "which ones to block. UI ready — app list coming soon.",
            )
        }
    }
}
