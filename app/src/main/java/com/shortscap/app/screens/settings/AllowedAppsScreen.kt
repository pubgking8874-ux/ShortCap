package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors

/**
 * Allowed Apps — UI only today. Future: apps that bypass restrictions, chosen
 * by the user (GET / UPDATE Allowed Apps backend APIs, or local DB).
 */
@Composable
fun AllowedAppsScreen(onBack: () -> Unit) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.monitoringAllowedApps, onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            ScEmptyState(
                icon = Icons.Filled.CheckCircle,
                title = strings.allowedAppsEmptyTitle,
                subtitle = strings.allowedAppsEmptyDesc,
            )
        }
    }
}
