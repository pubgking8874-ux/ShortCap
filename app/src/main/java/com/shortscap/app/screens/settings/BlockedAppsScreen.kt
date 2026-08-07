package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.theme.LocalScColors

/**
 * Blocked Apps — UI only today. Future: list installed applications here and
 * let the user pick which apps to block (GET / UPDATE Blocked Apps backend
 * APIs, or Android package manager + Usage Stats data).
 */
@Composable
fun BlockedAppsScreen(onBack: () -> Unit) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.monitoringBlockedApps, onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            ScEmptyState(
                iconKey = IconKey.BLOCKED_APPS,
                title = strings.blockedAppsEmptyTitle,
                subtitle = strings.blockedAppsEmptyDesc,
            )
        }
    }
}
