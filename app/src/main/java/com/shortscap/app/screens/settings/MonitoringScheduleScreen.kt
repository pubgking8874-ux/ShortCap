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
 * Monitoring Schedule — UI only today. Future: choose a Start Time, End Time,
 * and which days (Weekdays / Weekends) monitoring is active.
 */
@Composable
fun MonitoringScheduleScreen(onBack: () -> Unit) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.monitoringSchedule, onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            ScEmptyState(
                iconKey = IconKey.SCHEDULE,
                title = strings.scheduleEmptyTitle,
                subtitle = strings.scheduleEmptyDesc,
            )
        }
    }
}
