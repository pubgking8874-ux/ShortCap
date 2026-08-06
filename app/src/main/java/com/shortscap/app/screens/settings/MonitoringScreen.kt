package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.model.MonitoringSettings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Preset daily screen-time limits (minutes) offered by the picker dialog. */
private val ScreenTimePresets = listOf(
    15 to "15 min",
    30 to "30 min",
    45 to "45 min",
    60 to "1 Hour",
    120 to "2 Hours",
)

/** Break-reminder interval options (minutes). */
private val BreakReminderIntervals = listOf(
    15 to "15 Minutes",
    30 to "30 Minutes",
    45 to "45 Minutes",
    60 to "1 Hour",
)

/** Looks up a label for [value] in [options], falling back to [fallback]. */
private fun labelFor(value: Int, options: List<Pair<Int, String>>, fallback: (Int) -> String): String =
    options.firstOrNull { it.first == value }?.second ?: fallback(value)

/** Formats the current limit — preset label, or "Custom · N min" for custom. */
private fun formatLimit(minutes: Int): String =
    labelFor(minutes, ScreenTimePresets) { "Custom · ${it} min" }

/**
 * Monitoring Settings — the dedicated screen for every monitoring feature.
 *
 * Sections: Enable Monitoring (master switch), App Blocking, Daily Screen
 * Time Limit (picker), Blocked Apps, Allowed Apps, Strict Mode, Short Video
 * Platforms (data-driven switches — unlimited platforms), Break Reminder,
 * Monitoring Schedule, and read-only Statistics.
 *
 * All state is driven by [MonitoringSettings] passed from the ViewModel; the
 * screen never hardcodes business logic, so GET / UPDATE Monitoring Settings
 * backend APIs (or a local DB) can be swapped in behind the same shape with
 * no UI changes.
 */
@Composable
fun MonitoringScreen(
    settings: MonitoringSettings,
    onToggleMonitoring: (Boolean) -> Unit,
    onToggleAppBlocking: (Boolean) -> Unit,
    onSetScreenTimeLimit: (Int) -> Unit,
    onToggleStrictMode: (Boolean) -> Unit,
    onTogglePlatform: (String) -> Unit,
    onToggleBreakReminder: (Boolean) -> Unit,
    onSetBreakReminderInterval: (Int) -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenSchedule: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    var limitDialogOpen by remember { mutableStateOf(false) }
    var customLimitDialogOpen by remember { mutableStateOf(false) }
    var intervalDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = "Monitoring", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Section 1 — Monitoring (master switch) ----
            SectionTitle("Monitoring")
            ScPremiumNavCard(
                icon = Icons.Filled.Insights,
                title = "Enable Monitoring",
                subtitle = "Master switch for all monitoring features.",
                onClick = { onToggleMonitoring(!settings.enabled) },
                trailing = {
                    ScSwitch(on = settings.enabled, onToggle = { onToggleMonitoring(!settings.enabled) })
                },
            )

            // ---- Section 2 — App Blocking ----
            SectionTitle("App Blocking")
            ScPremiumNavCard(
                icon = Icons.Filled.Block,
                title = "Enable App Blocking",
                subtitle = "If OFF, all blocking features become disabled.",
                onClick = { onToggleAppBlocking(!settings.appBlockingEnabled) },
                trailing = {
                    ScSwitch(on = settings.appBlockingEnabled, onToggle = { onToggleAppBlocking(!settings.appBlockingEnabled) })
                },
            )

            // ---- Section 3 — Daily Screen Time Limit (picker dialog) ----
            SectionTitle("Daily Screen Time Limit")
            ScPremiumNavCard(
                icon = Icons.Filled.Timer,
                title = "Daily Screen Time Limit",
                onClick = { limitDialogOpen = true },
                trailing = { TrailingValue(formatLimit(settings.screenTimeLimitMinutes)) },
            )

            // ---- Section 4 — Blocked Apps (dedicated page, UI only) ----
            SectionTitle("Blocked Apps")
            ScPremiumNavCard(
                icon = Icons.Filled.DoNotDisturbOn,
                title = "Blocked Apps",
                onClick = onOpenBlockedApps,
            )

            // ---- Section 5 — Allowed Apps (dedicated page, UI only) ----
            SectionTitle("Allowed Apps")
            ScPremiumNavCard(
                icon = Icons.Filled.CheckCircle,
                title = "Allowed Apps",
                onClick = onOpenAllowedApps,
            )

            // ---- Section 6 — Strict Mode ----
            SectionTitle("Strict Mode")
            ScPremiumNavCard(
                icon = Icons.Filled.GppMaybe,
                title = "Strict Mode",
                subtitle = "Prevent bypassing restrictions.",
                onClick = { onToggleStrictMode(!settings.strictModeEnabled) },
                trailing = {
                    ScSwitch(on = settings.strictModeEnabled, onToggle = { onToggleStrictMode(!settings.strictModeEnabled) })
                },
            )

            // ---- Section 7 — Short Video Platforms (data-driven switches) ----
            SectionTitle("Short Video Platforms")
            settings.platforms.forEach { platform ->
                ScPremiumNavCard(
                    icon = Icons.Filled.SmartDisplay,
                    title = platform.name,
                    onClick = { onTogglePlatform(platform.id) },
                    trailing = {
                        ScSwitch(on = platform.enabled, onToggle = { onTogglePlatform(platform.id) })
                    },
                )
            }

            // ---- Section 8 — Break Reminder ----
            SectionTitle("Break Reminder")
            ScPremiumNavCard(
                icon = Icons.Filled.SelfImprovement,
                title = "Break Reminder",
                onClick = { onToggleBreakReminder(!settings.breakReminderEnabled) },
                trailing = {
                    ScSwitch(on = settings.breakReminderEnabled, onToggle = { onToggleBreakReminder(!settings.breakReminderEnabled) })
                },
            )
            ScPremiumNavCard(
                icon = Icons.Filled.Alarm,
                title = "Reminder Interval",
                onClick = { intervalDialogOpen = true },
                trailing = { TrailingValue(intervalLabel(settings.breakReminderIntervalMinutes)) },
            )

            // ---- Section 9 — Monitoring Schedule (dedicated page, UI only) ----
            SectionTitle("Monitoring Schedule")
            ScPremiumNavCard(
                icon = Icons.Filled.CalendarMonth,
                title = "Monitoring Schedule",
                onClick = onOpenSchedule,
            )

            // ---- Section 10 — Statistics (read-only demo values) ----
            SectionTitle("Statistics")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatTile(
                        icon = Icons.Filled.Schedule,
                        label = "Today's Usage",
                        value = settings.todayUsage,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = Icons.Filled.Block,
                        label = "Blocked Apps Count",
                        value = settings.blockedAppsCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatTile(
                        icon = Icons.Filled.Timer,
                        label = "Current Daily Limit",
                        value = formatLimit(settings.screenTimeLimitMinutes),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = Icons.Filled.MonitorHeart,
                        label = "Monitoring Status",
                        value = if (settings.enabled) "Active" else "Paused",
                        valueColor = if (settings.enabled) colors.Success else colors.Warning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ---- Picker dialogs ----
    if (limitDialogOpen) {
        AlertDialog(
            onDismissRequest = { limitDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text("Daily Screen Time Limit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScreenTimePresets.forEach { (minutes, label) ->
                        DialogOption(
                            label = label,
                            selected = settings.screenTimeLimitMinutes == minutes,
                            onClick = {
                                onSetScreenTimeLimit(minutes)
                                limitDialogOpen = false
                            },
                        )
                    }
                    DialogOption(
                        label = "Custom",
                        selected = ScreenTimePresets.none { it.first == settings.screenTimeLimitMinutes },
                        onClick = {
                            limitDialogOpen = false
                            customLimitDialogOpen = true
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { limitDialogOpen = false }) {
                    Text("Cancel", color = colors.TextSecondary)
                }
            },
        )
    }

    if (customLimitDialogOpen) {
        var input by remember {
            mutableStateOf(
                if (ScreenTimePresets.none { it.first == settings.screenTimeLimitMinutes }) {
                    settings.screenTimeLimitMinutes.toString()
                } else {
                    ""
                },
            )
        }
        val minutes = input.toIntOrNull()
        AlertDialog(
            onDismissRequest = { customLimitDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text("Custom Limit") },
            text = {
                Column {
                    Text("Set a daily screen time limit in minutes.", style = ScTextStyles.Body)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter(Char::isDigit).take(3) },
                        label = { Text("Minutes", color = colors.TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.Accent,
                            unfocusedBorderColor = colors.Divider,
                            focusedLabelColor = colors.Accent,
                            unfocusedLabelColor = colors.TextSecondary,
                            cursorColor = colors.Accent,
                            focusedTextColor = colors.TextPrimary,
                            unfocusedTextColor = colors.TextPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        minutes?.let { onSetScreenTimeLimit(it.coerceIn(1, 720)) }
                        customLimitDialogOpen = false
                    },
                    enabled = minutes != null && minutes > 0,
                ) {
                    Text(
                        "Set",
                        color = if (minutes != null && minutes > 0) colors.Accent else colors.TextDisabled,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { customLimitDialogOpen = false }) {
                    Text("Cancel", color = colors.TextSecondary)
                }
            },
        )
    }

    if (intervalDialogOpen) {
        AlertDialog(
            onDismissRequest = { intervalDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text("Reminder Interval") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BreakReminderIntervals.forEach { (minutes, label) ->
                        DialogOption(
                            label = label,
                            selected = settings.breakReminderIntervalMinutes == minutes,
                            onClick = {
                                onSetBreakReminderInterval(minutes)
                                intervalDialogOpen = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { intervalDialogOpen = false }) {
                    Text("Cancel", color = colors.TextSecondary)
                }
            },
        )
    }
}

private fun intervalLabel(minutes: Int): String =
    labelFor(minutes, BreakReminderIntervals) { "${it} Minutes" }

/** Uppercased section heading, matching the app's section-title style. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = LocalScColors.current.TextSecondary,
        style = ScTextStyles.SectionTitle,
    )
}

/** Small read-only stat card used in the Statistics section. */
@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalScColors.current.TextPrimary,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.StatIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(22.dp))
        }
        Text(
            value,
            color = valueColor,
            style = ScTextStyles.StatValue.copy(fontSize = 17.sp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(label, color = colors.TextSecondary, style = ScTextStyles.Caption)
    }
}

/** Right-aligned value + chevron used by picker rows. */
@Composable
private fun TrailingValue(value: String) {
    val colors = LocalScColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Selectable row inside the picker dialogs — highlights the current pick. */
@Composable
private fun DialogOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.ChipActiveBg else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            color = if (selected) colors.ChipActiveText else colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(18.dp))
        }
    }
}
