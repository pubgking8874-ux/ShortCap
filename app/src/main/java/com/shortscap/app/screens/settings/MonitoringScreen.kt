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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.model.MonitoringSettings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScCursorColor
import com.shortscap.app.theme.ScTextStyles

/**
 * Monitoring Settings — the dedicated screen for every monitoring feature.
 *
 * Sections: Enable Monitoring (master switch), App Blocking, Daily Screen
 * Time Limit (picker), Blocked Apps, Allowed Apps, Strict Mode, Short Video
 * Platforms (data-driven switches — unlimited platforms), Break Reminder,
 * Monitoring Schedule, and read-only Statistics.
 *
 * All state is driven by [MonitoringSettings] passed from the ViewModel; the
 * screen never hardcodes business logic or text (all labels come from the
 * active language catalog), so GET / UPDATE Monitoring Settings backend APIs
 * and new languages plug in without UI changes.
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
    val strings = LocalAppStrings.current
    var limitDialogOpen by remember { mutableStateOf(false) }
    var customLimitDialogOpen by remember { mutableStateOf(false) }
    var intervalDialogOpen by remember { mutableStateOf(false) }

    // Option lists and labels follow the active language catalog.
    val screenTimePresets = listOf(
        15 to strings.time15Min,
        30 to strings.time30Min,
        45 to strings.time45Min,
        60 to strings.time1Hour,
        120 to strings.time2Hours,
    )
    val breakIntervals = listOf(
        15 to strings.time15Minutes,
        30 to strings.time30Minutes,
        45 to strings.time45Minutes,
        60 to strings.time1Hour,
    )
    fun formatLimit(minutes: Int): String =
        screenTimePresets.firstOrNull { it.first == minutes }?.second
            ?: "${strings.timeCustom} · $minutes ${strings.minutesLabel}"
    fun intervalLabel(minutes: Int): String =
        breakIntervals.firstOrNull { it.first == minutes }?.second
            ?: "$minutes ${strings.minutesLabel}"

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.monitoringTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Section 1 — Monitoring (master switch) ----
            SectionTitle(strings.monitoringSection)
            ScPremiumNavCard(
                iconKey = IconKey.MONITORING_ENABLE,
                title = strings.monitoringEnable,
                subtitle = strings.monitoringEnableDesc,
                onClick = { onToggleMonitoring(!settings.enabled) },
                trailing = {
                    ScSwitch(on = settings.enabled, onToggle = { onToggleMonitoring(!settings.enabled) })
                },
            )

            // ---- Section 2 — App Blocking ----
            SectionTitle(strings.monitoringAppBlocking)
            ScPremiumNavCard(
                iconKey = IconKey.APP_BLOCKING,
                title = strings.monitoringEnableAppBlocking,
                subtitle = strings.monitoringEnableAppBlockingDesc,
                onClick = { onToggleAppBlocking(!settings.appBlockingEnabled) },
                trailing = {
                    ScSwitch(on = settings.appBlockingEnabled, onToggle = { onToggleAppBlocking(!settings.appBlockingEnabled) })
                },
            )

            // ---- Section 3 — Daily Screen Time Limit (picker dialog) ----
            SectionTitle(strings.monitoringDailyLimit)
            ScPremiumNavCard(
                iconKey = IconKey.SCREEN_TIME_LIMIT,
                title = strings.monitoringDailyLimit,
                onClick = { limitDialogOpen = true },
                trailing = { TrailingValue(formatLimit(settings.screenTimeLimitMinutes)) },
            )

            // ---- Section 4 — Blocked Apps (dedicated page, UI only) ----
            SectionTitle(strings.monitoringBlockedApps)
            ScPremiumNavCard(
                iconKey = IconKey.BLOCKED_APPS,
                title = strings.monitoringBlockedApps,
                onClick = onOpenBlockedApps,
            )

            // ---- Section 5 — Allowed Apps (dedicated page, UI only) ----
            SectionTitle(strings.monitoringAllowedApps)
            ScPremiumNavCard(
                iconKey = IconKey.ALLOWED_APPS,
                title = strings.monitoringAllowedApps,
                onClick = onOpenAllowedApps,
            )

            // ---- Section 6 — Strict Mode ----
            SectionTitle(strings.monitoringStrictMode)
            ScPremiumNavCard(
                iconKey = IconKey.STRICT_MODE,
                title = strings.monitoringStrictMode,
                subtitle = strings.monitoringStrictModeDesc,
                onClick = { onToggleStrictMode(!settings.strictModeEnabled) },
                trailing = {
                    ScSwitch(on = settings.strictModeEnabled, onToggle = { onToggleStrictMode(!settings.strictModeEnabled) })
                },
            )

            // ---- Section 7 — Short Video Platforms (data-driven switches) ----
            SectionTitle(strings.monitoringShortVideoPlatforms)
            settings.platforms.forEach { platform ->
                ScPremiumNavCard(
                    iconKey = IconKey.PLATFORM,
                    title = platform.name,
                    onClick = { onTogglePlatform(platform.id) },
                    trailing = {
                        ScSwitch(on = platform.enabled, onToggle = { onTogglePlatform(platform.id) })
                    },
                )
            }

            // ---- Section 8 — Break Reminder ----
            SectionTitle(strings.monitoringBreakReminder)
            ScPremiumNavCard(
                iconKey = IconKey.BREAK_REMINDER,
                title = strings.monitoringBreakReminder,
                onClick = { onToggleBreakReminder(!settings.breakReminderEnabled) },
                trailing = {
                    ScSwitch(on = settings.breakReminderEnabled, onToggle = { onToggleBreakReminder(!settings.breakReminderEnabled) })
                },
            )
            ScPremiumNavCard(
                iconKey = IconKey.REMINDER_INTERVAL,
                title = strings.monitoringReminderInterval,
                onClick = { intervalDialogOpen = true },
                trailing = { TrailingValue(intervalLabel(settings.breakReminderIntervalMinutes)) },
            )

            // ---- Section 9 — Monitoring Schedule (dedicated page, UI only) ----
            SectionTitle(strings.monitoringSchedule)
            ScPremiumNavCard(
                iconKey = IconKey.SCHEDULE,
                title = strings.monitoringSchedule,
                onClick = onOpenSchedule,
            )

            // ---- Section 10 — Statistics (read-only demo values) ----
            SectionTitle(strings.monitoringStatistics)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatTile(
                        iconKey = IconKey.STAT_TODAY_USAGE,
                        label = strings.monitoringTodayUsage,
                        value = settings.todayUsage,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        iconKey = IconKey.STAT_BLOCKED_COUNT,
                        label = strings.monitoringBlockedAppsCount,
                        value = settings.blockedAppsCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatTile(
                        iconKey = IconKey.STAT_CURRENT_LIMIT,
                        label = strings.monitoringCurrentDailyLimit,
                        value = formatLimit(settings.screenTimeLimitMinutes),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        iconKey = IconKey.STAT_MONITORING_STATUS,
                        label = strings.monitoringStatus,
                        value = if (settings.enabled) strings.monitoringActive else strings.monitoringPaused,
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
            title = { Text(strings.monitoringDailyLimit) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    screenTimePresets.forEach { (minutes, label) ->
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
                        label = strings.timeCustom,
                        selected = screenTimePresets.none { it.first == settings.screenTimeLimitMinutes },
                        onClick = {
                            limitDialogOpen = false
                            customLimitDialogOpen = true
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { limitDialogOpen = false }) {
                    Text(strings.cancel, color = colors.TextSecondary)
                }
            },
        )
    }

    if (customLimitDialogOpen) {
        var input by remember {
            mutableStateOf(
                if (screenTimePresets.none { it.first == settings.screenTimeLimitMinutes }) {
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
            title = { Text(strings.customLimitTitle) },
            text = {
                Column {
                    Text(strings.customLimitDesc, style = ScTextStyles.Body)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter(Char::isDigit).take(3) },
                        label = { Text(strings.minutesLabel, color = colors.TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.Accent,
                            unfocusedBorderColor = colors.Divider,
                            focusedLabelColor = colors.Accent,
                            unfocusedLabelColor = colors.TextSecondary,
                            cursorColor = ScCursorColor(),
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
                        strings.setLabel,
                        color = if (minutes != null && minutes > 0) colors.Accent else colors.TextDisabled,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { customLimitDialogOpen = false }) {
                    Text(strings.cancel, color = colors.TextSecondary)
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
            title = { Text(strings.monitoringReminderInterval) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    breakIntervals.forEach { (minutes, label) ->
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
                    Text(strings.cancel, color = colors.TextSecondary)
                }
            },
        )
    }
}

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
    icon: ImageVector? = null,
    iconKey: IconKey? = null,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalScColors.current.TextPrimary,
) {
    val colors = LocalScColors.current
    val style = LocalIconStyle.current
    val resolvedIcon = icon ?: iconKey?.let { IconTheme.icon(style, it) } ?: Icons.Filled.Info
    val resolvedTint = if (iconKey != null) IconTheme.tint(style, iconKey, colors.Accent) else colors.Accent
    val resolvedBg = colors.CardHover
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
                .background(resolvedBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(resolvedIcon, contentDescription = null, tint = resolvedTint, modifier = Modifier.size(22.dp))
        }
        Text(
            value,
            color = valueColor,
            style = ScTextStyles.StatValue.copy(fontSize = 17.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
