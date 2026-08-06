package com.shortscap.app.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.SettingsDestination
import com.shortscap.app.model.SettingsItem
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Premium Settings home — a clean list of large interactive cards. Every row
 * is ONLY icon + title + chevron (no subtitles, no inline expansion); each
 * item opens its own dedicated screen through [SettingsDestination], so
 * navigation behaves like a standard settings app:
 *
 *   Settings → <item> → Back → Settings
 *
 * Labels come from the active language catalog (LocalAppStrings).
 */
@Composable
fun SettingsScreen(
    onOpenDestination: (SettingsDestination) -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val settingsItems = listOf(
        SettingsItem(SettingsDestination.GENERAL, Icons.Filled.Tune, strings.settingsGeneral),
        SettingsItem(SettingsDestination.MONITORING, Icons.Filled.Visibility, strings.settingsMonitoring),
        SettingsItem(SettingsDestination.PERMISSIONS, Icons.Filled.VerifiedUser, strings.settingsPermissions),
        SettingsItem(SettingsDestination.NOTIFICATIONS, Icons.Filled.Notifications, strings.settingsNotifications),
        SettingsItem(SettingsDestination.APPEARANCE, Icons.Filled.Palette, strings.settingsAppearance),
        SettingsItem(SettingsDestination.PRIVACY, Icons.Filled.Lock, strings.settingsPrivacy),
        SettingsItem(SettingsDestination.DATA_BACKUP, Icons.Filled.Storage, strings.settingsDataBackup),
        SettingsItem(SettingsDestination.ABOUT, Icons.Filled.Info, strings.settingsAbout),
        SettingsItem(SettingsDestination.RESET_ALL, Icons.Filled.RestartAlt, strings.settingsResetAll),
    )

    // Scrolling is provided by the shared ScNavHost container (same as Home /
    // Activity / Web), so no nested verticalScroll is needed here.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(strings.settingsTitle, color = colors.TextPrimary, style = ScTextStyles.H1)

        settingsItems.forEach { item ->
            ScPremiumNavCard(
                icon = item.icon,
                title = item.label,
                onClick = { onOpenDestination(item.destination) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
