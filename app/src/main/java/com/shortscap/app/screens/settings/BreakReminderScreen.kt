package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScDurationWheelDialog
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.study.BreakReminderConfig
import com.shortscap.app.study.BreakReminderConflict
import com.shortscap.app.study.BreakReminderPattern
import com.shortscap.app.study.BreakReminderPresetMinutes
import com.shortscap.app.study.BreakReminderSound
import com.shortscap.app.study.StudyScheduleEntry
import com.shortscap.app.study.breakReminderConflicts
import com.shortscap.app.study.formatStudyClock
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Break Reminder configuration page (Journal → Study Mode → Break Reminder).
 *
 * Break Reminder is a full configurable feature, NOT a plain ON/OFF switch:
 *   • Enable/disable — master switch; configuration controls are dimmed and
 *     locked while OFF.
 *   • Remind Me After — preset intervals (15/20/25/30/45/60 minutes) or a
 *     Custom value via the app-wide scroll-wheel duration picker (no +/−).
 *   • Reminder Pattern — Once (a single reminder) or Repeat (every interval
 *     while Study Mode is active).
 *   • Reminder Sound — front-end preference (Default / Soft Bell / Gentle
 *     Chime / Focus Tone / Custom); playback connects later.
 *   • Schedule conflict check — before saving, the reminder cycle is
 *     simulated against the user's Study Schedule; overlaps are shown on a
 *     small timeline and the user chooses [Adjust Reminder] or
 *     [Keep Schedule]. The schedules are NEVER modified.
 *
 * The configuration is saved as one [BreakReminderConfig] into
 * StudyModeSettings — Study Mode sessions and the future backend read it
 * from the same single source.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakReminderScreen(
    config: BreakReminderConfig,
    schedules: List<StudyScheduleEntry>,
    onSave: (BreakReminderConfig) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var enabled by remember(config.enabled) { mutableStateOf(config.enabled) }
    var interval by remember(config.intervalMinutes) { mutableStateOf(config.intervalMinutes) }
    var pattern by remember(config.pattern) { mutableStateOf(config.pattern) }
    var sound by remember(config.sound) { mutableStateOf(config.sound) }
    var infoOpen by remember { mutableStateOf(false) }
    var customDialogOpen by remember { mutableStateOf(false) }
    var soundDialogOpen by remember { mutableStateOf(false) }
    var conflictDialogOpen by remember { mutableStateOf(false) }

    val draft = BreakReminderConfig(enabled, interval, pattern, sound)
    // Simulated overlap with the user's Study Schedule — warning only; the
    // schedules are never touched. Only shown when a real conflict exists.
    val conflicts = remember(enabled, interval, pattern, schedules) {
        breakReminderConflicts(draft, schedules)
    }
    val isCustomInterval = interval !in BreakReminderPresetMinutes
    val summary = if (enabled) {
        "${strings.studyBreakReminderEvery} ${strings.studyDurationText(interval)}"
    } else {
        strings.studyBreakReminderOff
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        // Top bar — the info (ⓘ) icon sits on the SAME line as the title,
        // aligned to the far right.
        ScSubScreenTopBar(
            title = strings.studyBreakReminder,
            onBack = onBack,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.CardHover)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { infoOpen = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = strings.studyBreakReminder,
                        tint = colors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Enable / disable — master switch ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.Card, RoundedCornerShape(22.dp))
                    .border(1.dp, colors.Divider, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(colors.CardHover),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.LocalCafe,
                        contentDescription = null,
                        tint = colors.Accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.studyBreakReminder, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp))
                    Text(summary, color = colors.TextSecondary, style = ScTextStyles.Body)
                }
                ScSwitch(on = enabled, onToggle = { enabled = !enabled })
            }

            // ---- Configuration — dimmed + locked while disabled ----
            Column(
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ---- Remind Me After ----
                SectionTitle(strings.studyBreakReminderIntervalLabel)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BreakReminderPresetMinutes.forEach { minutes ->
                        ChoiceChip(
                            label = strings.studyDurationText(minutes),
                            selected = !isCustomInterval && interval == minutes,
                            enabled = enabled,
                            onClick = { interval = minutes },
                        )
                    }
                    ChoiceChip(
                        label = strings.studyBreakReminderCustom,
                        selected = isCustomInterval,
                        enabled = enabled,
                        onClick = { customDialogOpen = true },
                    )
                }

                // ---- Reminder Pattern: Once / Repeat ----
                SectionTitle(strings.studyBreakReminderPatternLabel)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip(
                        label = strings.studyBreakReminderPatternOnce,
                        selected = pattern == BreakReminderPattern.ONCE,
                        enabled = enabled,
                        onClick = { pattern = BreakReminderPattern.ONCE },
                    )
                    ChoiceChip(
                        label = strings.studyBreakReminderPatternRepeat,
                        selected = pattern == BreakReminderPattern.REPEAT,
                        enabled = enabled,
                        onClick = { pattern = BreakReminderPattern.REPEAT },
                    )
                }

                // ---- Reminder Sound ----
                ScPremiumNavCard(
                    iconKey = IconKey.NOTIF_SOUND,
                    title = strings.studyBreakReminderSoundLabel,
                    onClick = { if (enabled) soundDialogOpen = true },
                    trailing = {
                        TrailingValue(breakSoundLabel(strings, sound))
                    },
                )

                // ---- Schedule conflict preview — ONLY when a real conflict
                //      exists (never a permanent block of text). When the
                //      reminder cycle is clear, a quiet positive caption. ----
                if (conflicts.isNotEmpty()) {
                    SectionTitle(strings.studyBreakConflictTitle)
                    conflicts.forEach { conflict ->
                        ConflictCard(conflict = conflict, intervalMinutes = interval)
                    }
                } else if (enabled) {
                    Text(
                        strings.studyBreakConflictNone,
                        color = colors.TextSecondary,
                        style = ScTextStyles.Caption,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // ---- Save Reminder ----
            ScButton(
                label = strings.studyBreakReminderSave,
                onClick = {
                    if (enabled && conflicts.isNotEmpty()) {
                        conflictDialogOpen = true
                    } else {
                        onSave(draft)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // ---- Info (ⓘ) popup ----
    if (infoOpen) {
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.studyBreakReminder) },
            text = { Text(strings.studyBreakReminderInfoDesc, style = ScTextStyles.Body) },
            confirmButton = {
                TextButton(onClick = { infoOpen = false }) {
                    Text(strings.ok, color = colors.Accent)
                }
            },
        )
    }

    // ---- Custom interval — the app-wide scroll-wheel duration picker ----
    if (customDialogOpen) {
        ScDurationWheelDialog(
            title = strings.studyBreakReminderIntervalLabel,
            initialMinutes = interval,
            valueLabel = strings::studyDurationText,
            onConfirm = { interval = it; customDialogOpen = false },
            onCancel = { customDialogOpen = false },
        )
    }

    // ---- Reminder Sound picker ----
    if (soundDialogOpen) {
        AlertDialog(
            onDismissRequest = { soundDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.studyBreakReminderSoundLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BreakReminderSound.entries.forEach { option ->
                        DialogOption(
                            label = breakSoundLabel(strings, option),
                            selected = sound == option,
                            onClick = { sound = option; soundDialogOpen = false },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { soundDialogOpen = false }) {
                    Text(strings.cancel, color = colors.TextSecondary)
                }
            },
        )
    }

    // ---- Schedule conflict warning — shown only when saving would overlap
    //      a scheduled session. [Adjust Reminder] returns to the page;
    //      [Keep Schedule] saves the reminder without touching the schedule. ----
    if (conflictDialogOpen && conflicts.isNotEmpty()) {
        val firstConflict = conflicts.first()
        AlertDialog(
            onDismissRequest = { conflictDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.studyBreakConflictTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        strings.studyBreakConflictMessage(formatStudyClock(firstConflict.reminderTimes.first())),
                        style = ScTextStyles.Body,
                    )
                    ConflictCard(conflict = firstConflict, intervalMinutes = interval)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    conflictDialogOpen = false
                    onSave(draft)
                }) {
                    Text(strings.studyBreakConflictKeepSchedule, color = colors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { conflictDialogOpen = false }) {
                    Text(strings.studyBreakConflictAdjustReminder, color = colors.TextSecondary)
                }
            },
        )
    }
}

/** Localized label for a [BreakReminderSound] option. */
private fun breakSoundLabel(strings: AppStrings, sound: BreakReminderSound): String = when (sound) {
    BreakReminderSound.DEFAULT -> strings.studyBreakSoundDefault
    BreakReminderSound.SOFT_BELL -> strings.studyBreakSoundSoftBell
    BreakReminderSound.GENTLE_CHIME -> strings.studyBreakSoundGentleChime
    BreakReminderSound.FOCUS_TONE -> strings.studyBreakSoundFocusTone
    BreakReminderSound.CUSTOM -> strings.studyBreakSoundCustom
}

/** One compact conflict card — scheduled window timeline + conflicting times. */
@Composable
private fun ConflictCard(
    conflict: BreakReminderConflict,
    intervalMinutes: Int,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val schedule = conflict.schedule
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.studyBreakConflictScheduledStudy, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
            Text(
                "${formatStudyClock(schedule.startMinutes)} – ${formatStudyClock(schedule.startMinutes + schedule.durationMinutes)}",
                color = colors.TextSecondary,
                style = ScTextStyles.Caption,
            )
        }
        ConflictTimeline(schedule = schedule, times = conflict.reminderTimes)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.studyBreakConflictLabel, color = colors.Danger, style = ScTextStyles.BodySemiBold)
            Text(
                conflict.reminderTimes.joinToString(", ") { formatStudyClock(it) },
                color = colors.TextPrimary,
                style = ScTextStyles.Body,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.studyBreakReminder, color = colors.TextSecondary, style = ScTextStyles.BodySemiBold)
            Text(
                "${strings.studyBreakReminderEvery} ${strings.studyDurationText(intervalMinutes)}",
                color = colors.TextPrimary,
                style = ScTextStyles.Body,
            )
        }
    }
}

/**
 * Mini timeline for one scheduled window: the bar spans the session, ticks
 * mark the reminder times that land inside it (danger red = conflict).
 */
@Composable
private fun ConflictTimeline(schedule: StudyScheduleEntry, times: List<Int>) {
    val colors = LocalScColors.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.CardHover),
    ) {
        val barWidth = maxWidth
        val duration = schedule.durationMinutes.coerceAtLeast(1)
        times.forEach { time ->
            val fraction = ((time - schedule.startMinutes).toFloat() / duration).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .offset(x = (barWidth.value * fraction).dp - 1.dp)
                    .width(2.dp)
                    .height(12.dp)
                    .background(colors.Danger),
            )
        }
    }
}

/** Green-highlighted choice chip (selected = solid Success + white text). */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.Success else colors.CardHover, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) Color.White else colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 13.sp),
        )
    }
}

/** Selectable row inside the sound picker — checkmark on the current pick. */
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

/** Uppercased section heading, matching the app's section-title style. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = LocalScColors.current.TextSecondary,
        style = ScTextStyles.SectionTitle,
    )
}

/** Right-aligned value + chevron used by the sound row. */
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
