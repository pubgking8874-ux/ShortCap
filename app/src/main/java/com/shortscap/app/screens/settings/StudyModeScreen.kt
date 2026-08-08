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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.study.StudyModeSettings
import com.shortscap.app.study.StudySoundMode
import com.shortscap.app.study.StudySummary
import com.shortscap.app.study.formatStudyClock
import com.shortscap.app.study.formatStudyCountdown
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Study Mode — the complete Study Mode feature, living inside the EXISTING
 * General settings section (General → Study Mode). No new navigation item,
 * no duplicate controls anywhere else in the app.
 *
 * Sections:
 *   STATUS     — Active / Inactive + live countdown while a session runs.
 *   SESSION    — Start Study Session (with a pre-start confirmation that
 *                clearly states there is NO Stop/Cancel during a session and
 *                that Restricted Mode stays on until 00:00). While active the
 *                button is replaced by the live countdown — there is no way
 *                to stop early.
 *   SETTINGS   — Study Duration, Break Reminder, Break Duration, Sound Mode.
 *   SCHEDULE   — Study Schedule (enabled + start/end window).
 *   ALLOWED    — Allowed Apps/Websites (stays accessible during sessions).
 *   SUMMARY    — Study Session Summary (derived when sessions complete).
 *
 * All values flow from [StudyModeSettings] / [StudySession] / [StudySummary]
 * held in AppUiState — never hardcoded — and the session is timestamp-based
 * (startTime/endTime/currentTime), so the countdown stays accurate across
 * backgrounding. Study Mode data is fully separate from monitoring, shorts,
 * activity and history data.
 */
@Composable
fun StudyModeScreen(
    settings: StudyModeSettings,
    studyModeActive: Boolean,
    studyRemainingMillis: Long,
    studyTotalMillis: Long,
    summary: StudySummary,
    onStartSession: () -> Unit,
    onSetStudyDuration: (Int) -> Unit,
    onSetStudyBreakReminder: (Boolean) -> Unit,
    onSetStudyBreakDuration: (Int) -> Unit,
    onSetStudySoundMode: (StudySoundMode) -> Unit,
    onSetStudyScheduleEnabled: (Boolean) -> Unit,
    onSetStudyScheduleStart: (Int) -> Unit,
    onSetStudyScheduleEnd: (Int) -> Unit,
    onOpenAllowed: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var startDialogOpen by remember { mutableStateOf(false) }
    var durationDialogOpen by remember { mutableStateOf(false) }
    var breakDurationDialogOpen by remember { mutableStateOf(false) }
    var soundDialogOpen by remember { mutableStateOf(false) }
    var scheduleStartDialogOpen by remember { mutableStateOf(false) }
    var scheduleEndDialogOpen by remember { mutableStateOf(false) }

    val studyDurations = listOf(15, 25, 30, 45, 60, 90)
    val breakDurations = listOf(3, 5, 10, 15)
    val scheduleHours = listOf(6, 8, 10, 12, 14, 16, 18, 20, 22)
    val soundOptions = listOf(
        StudySoundMode.SOUND to strings.studySoundSound,
        StudySoundMode.VIBRATE to strings.studySoundVibrate,
        StudySoundMode.SILENT to strings.studySoundSilent,
    )

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.studyTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Status ----
            SectionTitle(strings.studyStatusLabel)
            StudyStatusCard(
                active = studyModeActive,
                remainingText = formatStudyCountdown(studyRemainingMillis),
                note = strings.studyRestrictionNote,
            )

            // ---- Session ----
            SectionTitle(strings.studySessionSection)
            if (studyModeActive) {
                ActiveSessionCard(
                    remainingMillis = studyRemainingMillis,
                    totalMillis = studyTotalMillis,
                )
            } else {
                ScButton(
                    label = strings.studyStartSession,
                    variant = ScButtonVariant.PRIMARY,
                    onClick = { startDialogOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Settings ----
            SectionTitle(strings.studySettingsSection)
            ScPremiumNavCard(
                iconKey = IconKey.STUDY_MODE,
                title = strings.studyDuration,
                onClick = { durationDialogOpen = true },
                trailing = { TrailingValue("${settings.studyDurationMinutes} ${strings.minutesLabel}") },
            )
            ScPremiumNavCard(
                iconKey = IconKey.BREAK_REMINDER,
                title = strings.studyBreakReminder,
                onClick = { onSetStudyBreakReminder(!settings.breakReminderEnabled) },
                trailing = {
                    ScSwitch(on = settings.breakReminderEnabled, onToggle = { onSetStudyBreakReminder(!settings.breakReminderEnabled) })
                },
            )
            ScPremiumNavCard(
                iconKey = IconKey.REMINDER_INTERVAL,
                title = strings.studyBreakDuration,
                onClick = { breakDurationDialogOpen = true },
                trailing = { TrailingValue("${settings.breakDurationMinutes} ${strings.minutesLabel}") },
            )
            ScPremiumNavCard(
                iconKey = IconKey.NOTIF_SOUND,
                title = strings.studySoundMode,
                onClick = { soundDialogOpen = true },
                trailing = {
                    TrailingValue(soundOptions.firstOrNull { it.first == settings.soundMode }?.second ?: "")
                },
            )

            // ---- Schedule ----
            SectionTitle(strings.studySchedule)
            ScPremiumNavCard(
                iconKey = IconKey.SCHEDULE,
                title = strings.studySchedule,
                onClick = { onSetStudyScheduleEnabled(!settings.schedule.enabled) },
                trailing = {
                    ScSwitch(on = settings.schedule.enabled, onToggle = { onSetStudyScheduleEnabled(!settings.schedule.enabled) })
                },
            )
            ScPremiumNavCard(
                iconKey = IconKey.SCHEDULE,
                title = strings.studyScheduleStart,
                onClick = { scheduleStartDialogOpen = true },
                trailing = { TrailingValue(formatStudyClock(settings.schedule.startMinutes)) },
            )
            ScPremiumNavCard(
                iconKey = IconKey.SCHEDULE,
                title = strings.studyScheduleEnd,
                onClick = { scheduleEndDialogOpen = true },
                trailing = { TrailingValue(formatStudyClock(settings.schedule.endMinutes)) },
            )

            // ---- Allowed Apps/Websites ----
            SectionTitle(strings.studyAllowedItems)
            ScPremiumNavCard(
                iconKey = IconKey.ALLOWED_APPS,
                title = strings.studyAllowedItems,
                subtitle = strings.studyAllowedItemsDesc,
                onClick = onOpenAllowed,
            )

            // ---- Summary ----
            SectionTitle(strings.studySummary)
            StudySummaryCard(summary = summary)
        }
    }

    // ---- Pre-start confirmation: no stop/cancel, Restricted Mode until 00:00 ----
    if (startDialogOpen) {
        AlertDialog(
            onDismissRequest = { startDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.studyStartConfirmTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.studyStartConfirmMessage, style = ScTextStyles.Body)
                    Text(strings.studyStartConfirmRestrictions, style = ScTextStyles.Body)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startDialogOpen = false
                    onStartSession()
                }) {
                    Text(strings.studyStartConfirmStart, color = colors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { startDialogOpen = false }) {
                    Text(strings.cancel, color = colors.TextSecondary)
                }
            },
        )
    }

    // ---- Picker dialogs ----
    PickerDialog(
        title = strings.studyDuration,
        open = durationDialogOpen,
        onDismiss = { durationDialogOpen = false },
        options = studyDurations.map { it to "${it} ${strings.minutesLabel}" },
        selected = settings.studyDurationMinutes,
        onSelect = { durationDialogOpen = false; onSetStudyDuration(it) },
    )
    PickerDialog(
        title = strings.studyBreakDuration,
        open = breakDurationDialogOpen,
        onDismiss = { breakDurationDialogOpen = false },
        options = breakDurations.map { it to "${it} ${strings.minutesLabel}" },
        selected = settings.breakDurationMinutes,
        onSelect = { breakDurationDialogOpen = false; onSetStudyBreakDuration(it) },
    )
    PickerDialog(
        title = strings.studySoundMode,
        open = soundDialogOpen,
        onDismiss = { soundDialogOpen = false },
        options = soundOptions.map { (mode, label) -> mode.ordinal to label },
        selected = settings.soundMode.ordinal,
        onSelect = { soundDialogOpen = false; onSetStudySoundMode(StudySoundMode.entries[it]) },
    )
    PickerDialog(
        title = strings.studyScheduleStart,
        open = scheduleStartDialogOpen,
        onDismiss = { scheduleStartDialogOpen = false },
        options = scheduleHours.map { it * 60 to formatStudyClock(it * 60) },
        selected = settings.schedule.startMinutes,
        onSelect = { scheduleStartDialogOpen = false; onSetStudyScheduleStart(it) },
    )
    PickerDialog(
        title = strings.studyScheduleEnd,
        open = scheduleEndDialogOpen,
        onDismiss = { scheduleEndDialogOpen = false },
        options = scheduleHours.map { it * 60 to formatStudyClock(it * 60) },
        selected = settings.schedule.endMinutes,
        onSelect = { scheduleEndDialogOpen = false; onSetStudyScheduleEnd(it) },
    )
}

/** Status header — Active with the live countdown, or Inactive. */
@Composable
private fun StudyStatusCard(active: Boolean, remainingText: String, note: String) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (active) colors.Success.copy(alpha = 0.16f) else colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = if (active) colors.Success else colors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (active) strings.studyStatusActive else strings.studyStatusInactive,
                color = if (active) colors.Success else colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            )
            if (active) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${strings.studyRemaining}: $remainingText",
                    color = colors.TextSecondary,
                    style = ScTextStyles.Body,
                )
            }
        }
    }
}

/** Active session card — big countdown, progress and the no-stop note. */
@Composable
private fun ActiveSessionCard(remainingMillis: Long, totalMillis: Long) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val progress = if (totalMillis > 0) {
        (1f - remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    } else 0f
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Accent.copy(alpha = 0.35f), shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            formatStudyCountdown(remainingMillis),
            color = colors.TextPrimary,
            style = ScTextStyles.BigStat,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.CardHover),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceAtLeast(0.02f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.Accent),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            strings.studyRestrictionNote,
            color = colors.TextSecondary,
            style = ScTextStyles.Caption,
            textAlign = TextAlign.Center,
        )
    }
}

/** Study Session Summary — derived statistics, one labeled row each. */
@Composable
private fun StudySummaryCard(summary: StudySummary) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        SummaryRow(strings.studySummarySessionsToday, "${summary.sessionsToday}")
        ScDivider(modifier = Modifier.padding(vertical = 2.dp))
        SummaryRow(strings.studySummaryTimeToday, "${summary.minutesToday} ${strings.minutesLabel}")
        ScDivider(modifier = Modifier.padding(vertical = 2.dp))
        SummaryRow(
            strings.studySummaryLastSession,
            summary.lastSessionDurationMinutes?.let { "$it ${strings.minutesLabel}" } ?: strings.studySummaryNone,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = colors.TextSecondary, style = ScTextStyles.Body)
        Text(value, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
    }
}

/** Reusable single-select picker dialog with a checkmark on the current pick. */
@Composable
private fun PickerDialog(
    title: String,
    open: Boolean,
    onDismiss: () -> Unit,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    if (!open) return
    val colors = LocalScColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextSecondary,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (value, label) ->
                    DialogOption(
                        label = label,
                        selected = selected == value,
                        onClick = { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalAppStrings.current.cancel, color = colors.TextSecondary)
            }
        },
    )
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
