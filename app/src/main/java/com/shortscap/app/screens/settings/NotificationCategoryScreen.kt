package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.notifications.NotificationCategory
import com.shortscap.app.notifications.NotificationSetting
import com.shortscap.app.notifications.NotificationSettingId
import com.shortscap.app.theme.LocalScColors

/**
 * Dedicated page for one notification category (Reminder Notifications,
 * Limit Alerts, ...). Shows every option in the category as a premium card —
 * icon, title, description and an on/off switch. No expandable layouts and no
 * navigation deeper than this page: each option is a plain toggle whose state
 * is held by the ViewModel and persisted locally (backend-ready via
 * [com.shortscap.app.notifications.NotificationRepository]).
 */
@Composable
fun NotificationCategoryScreen(
    category: NotificationCategory,
    settings: List<NotificationSetting>,
    onToggleSetting: (NotificationSettingId, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    // The category's options in stable enum order — every id maps 1:1 to a
    // future backend setting entry.
    val options = NotificationSettingId.entries.filter { it.category == category }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = notificationCategoryTitle(category, strings), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            options.forEach { id ->
                val setting = settings.firstOrNull { it.id == id } ?: NotificationSetting(id = id)
                ScPremiumNavCard(
                    icon = notificationSettingIcon(id),
                    title = notificationSettingTitle(id, strings),
                    subtitle = notificationSettingDescription(id, strings),
                    onClick = { onToggleSetting(id, !setting.enabled) },
                    trailing = {
                        ScSwitch(on = setting.enabled, onToggle = { onToggleSetting(id, !setting.enabled) })
                    },
                )
            }
        }
    }
}
