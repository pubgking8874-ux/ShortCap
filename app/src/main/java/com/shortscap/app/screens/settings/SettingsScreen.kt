package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.model.SettingsCategory
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.theme.ThemeMode

private val categories = listOf(
    SettingsCategory("general", Icons.Filled.Tune, "General", "Language, sync, defaults"),
    SettingsCategory("monitoring", Icons.Filled.Visibility, "Monitoring", "Screen & app tracking"),
    SettingsCategory("permissions", Icons.Filled.VerifiedUser, "Permissions", "Accessibility, usage access"),
    SettingsCategory("notifications", Icons.Filled.Notifications, "Notifications", "Alerts & reminders"),
    SettingsCategory("appearance", Icons.Filled.Palette, "Appearance", "Theme, accent color"),
    SettingsCategory("privacy", Icons.Filled.Description, "Privacy", "Data sharing, visibility"),
    SettingsCategory("backup", Icons.Filled.Storage, "Data Backup", "Cloud sync, export"),
    SettingsCategory("about", Icons.Filled.Info, "About", "Version, licenses"),
)

/** Mirrors function SettingsScreen() { ... } */
@Composable
fun SettingsScreen(
    expandedCategory: String?,
    onToggleCategory: (String) -> Unit,
    monitoringEnabled: Boolean,
    onMonitoringChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onResetAll: () -> Unit,
) {
    val colors = LocalScColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", color = colors.TextPrimary, style = ScTextStyles.H1)

        ScCard(modifier = Modifier.fillMaxWidth()) {
            categories.forEachIndexed { index, category ->
                CategoryRow(
                    category = category,
                    expanded = expandedCategory == category.key,
                    onClick = { onToggleCategory(category.key) },
                ) {
                    when (category.key) {
                        "monitoring" -> {
                            SettingRow("Active monitoring") { ScSwitch(on = monitoringEnabled, onToggle = { onMonitoringChange(!monitoringEnabled) }) }
                            SettingRow("Track app usage") { ScSwitch(on = true, onToggle = {}) }
                            SettingRow("Location snapshots") { ScSwitch(on = false, onToggle = {}) }
                        }
                        "notifications" -> {
                            SettingRow("Daily summary") { ScSwitch(on = notificationsEnabled, onToggle = { onNotificationsChange(!notificationsEnabled) }) }
                            SettingRow("Limit reached alerts") { ScSwitch(on = true, onToggle = {}) }
                        }
                        "appearance" -> {
                            ThemeSelector(
                                themeMode = themeMode,
                                onThemeModeChange = onThemeModeChange,
                            )
                        }
                        "about" -> {
                            Text(
                                "ShortsCap v2.4.1 · Build 2026072801\n© 2026 ShortsCap",
                                color = colors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                            )
                        }
                        else -> {
                            Text("Nothing to configure yet.", color = colors.TextDisabled, fontSize = 12.sp)
                        }
                    }
                }
                if (index < categories.size - 1) ScDivider()
            }
        }

        ScButton(
            label = "Reset All Settings",
            variant = ScButtonVariant.DANGER,
            onClick = onResetAll,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Mirrors the Appearance > Theme selector: Dark / Light / System Default.
 * Material 3 radio rows — exactly one selected at a time, with a clear
 * visual indicator (highlight + radio dot). Persisted via the ViewModel.
 */
@Composable
private fun ThemeSelector(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val colors = LocalScColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { mode ->
            val selected = themeMode == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.ChipActiveBg else Color.Transparent)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onThemeModeChange(mode) },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(mode.displayName, color = colors.TextPrimary, style = ScTextStyles.Body)
                RadioButton(
                    selected = selected,
                    onClick = null,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

private val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.DARK -> "Dark"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.SYSTEM -> "System Default"
    }

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    expanded: Boolean,
    onClick: () -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    val colors = LocalScColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 13.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.CardHover),
                contentAlignment = Alignment.Center,
            ) {
                Icon(category.icon, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(category.label, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
                Text(category.sub, color = colors.TextSecondary, style = ScTextStyles.Caption)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.TextSecondary,
                modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 59.dp, end = 10.dp, top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                expandedContent()
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: @Composable () -> Unit) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.TextSecondary, fontSize = 13.sp)
        value()
    }
}
