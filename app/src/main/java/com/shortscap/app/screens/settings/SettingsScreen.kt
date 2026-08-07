package com.shortscap.app.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
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
 * "Reset All Settings" is the LAST row and is the only exception: it never
 * navigates — tapping it opens the premium [ResetAllSettingsDialog] in place.
 * Labels come from the active language catalog (LocalAppStrings).
 */
@Composable
fun SettingsScreen(
    onOpenDestination: (SettingsDestination) -> Unit,
    onResetAll: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var showResetDialog by remember { mutableStateOf(false) }
    // Icons are requested through the centralized icon system (IconKey + the
    // active IconStyle), so the selected style colors every Settings row.
    val settingsItems = listOf(
        SettingsItem(SettingsDestination.GENERAL, IconKey.GENERAL, strings.settingsGeneral),
        SettingsItem(SettingsDestination.MONITORING, IconKey.MONITORING, strings.settingsMonitoring),
        SettingsItem(SettingsDestination.PERMISSIONS, IconKey.PERMISSIONS, strings.settingsPermissions),
        SettingsItem(SettingsDestination.NOTIFICATIONS, IconKey.NOTIFICATIONS, strings.settingsNotifications),
        SettingsItem(SettingsDestination.APPEARANCE, IconKey.APPEARANCE, strings.settingsAppearance),
        SettingsItem(SettingsDestination.ABOUT, IconKey.ABOUT, strings.settingsAbout),
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
                iconKey = item.iconKey,
                title = item.label,
                onClick = { onOpenDestination(item.destination) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Reset All Settings — always the last row. Opens the confirmation
        // dialog in place; never navigates to a separate screen.
        ScPremiumNavCard(
            iconKey = IconKey.RESET_ALL,
            title = strings.settingsResetAll,
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showResetDialog) {
        ResetAllSettingsDialog(
            onDismiss = { showResetDialog = false },
            onReset = {
                showResetDialog = false
                onResetAll()
            },
        )
    }
}
