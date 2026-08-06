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
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.notifications.NotificationCategory
import com.shortscap.app.theme.LocalScColors

/**
 * Notifications hub — a premium overview of the 6 notification categories.
 *
 * NOT a request page and NOT a flat option list: each row (icon · title ·
 * chevron, no subtitles) opens its own dedicated page, so nothing expands
 * inline. Option states live in the ViewModel ([NotificationSetting] list,
 * persisted locally) and are backend-ready via [com.shortscap.app.notifications.NotificationRepository].
 */
@Composable
fun NotificationsScreen(
    onOpenCategory: (NotificationCategory) -> Unit,
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
            // Every category opens its own dedicated page — icon + title +
            // chevron only (no subtitles on the main page).
            NotificationCategory.entries.forEach { category ->
                ScPremiumNavCard(
                    icon = notificationCategoryIcon(category),
                    title = notificationCategoryTitle(category, strings),
                    onClick = { onOpenCategory(category) },
                )
            }
        }
    }
}
