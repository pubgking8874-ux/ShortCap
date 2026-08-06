package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors

/**
 * Notifications — dedicated screen with premium toggle cards. Replaces the
 * old inline expansion; state lives in the ViewModel (backend-ready).
 */
@Composable
fun NotificationsScreen(
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.notificationsTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumNavCard(
                icon = Icons.Filled.NotificationsActive,
                title = strings.notifDailySummary,
                onClick = { onToggleNotifications(!notificationsEnabled) },
                trailing = {
                    ScSwitch(on = notificationsEnabled, onToggle = { onToggleNotifications(!notificationsEnabled) })
                },
            )
            ScPremiumNavCard(
                icon = Icons.Filled.Timelapse,
                title = strings.notifLimitAlerts,
                onClick = { /* static until backend notification prefs land */ },
                trailing = { ScSwitch(on = true, onToggle = {}) },
            )
        }
    }
}
