package com.shortscap.app.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScClockWheelDialog
import com.shortscap.app.components.ScDurationWheelDialog
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.study.StudyDay
import com.shortscap.app.study.StudyDaysInOrder
import com.shortscap.app.study.StudyScheduleEntry
import com.shortscap.app.study.formatStudyClock
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles


/**
 * Study Schedule — the full schedule management screen (General → Study
 * Mode → Study Schedule). Lists every scheduled Study Mode session as a
 * compact card: subject, selected days, start time · duration, reminder,
 * per-schedule enabled switch, plus Edit and Delete actions. Schedules are
 * independent — editing one never touches another.
 */
@Composable
fun StudyScheduleScreen(
    schedules: List<StudyScheduleEntry>,
    onToggleEnabled: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.studySchedule, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (schedules.isEmpty()) {
                ScheduleEmptyState()
            } else {
                schedules.forEach { entry ->
                    ScheduleCard(
                        entry = entry,
                        onToggleEnabled = { onToggleEnabled(entry.id) },
                        onEdit = { onEdit(entry.id) },
                        onDelete = { onDelete(entry.id) },
                    )
                }
            }
            ScButton(
                label = strings.studyScheduleAdd,
                icon = Icons.Filled.Add,
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One schedule card — subject, days, time · duration, reminder, toggle, actions. */
@Composable
private fun ScheduleCard(
    entry: StudyScheduleEntry,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val style = LocalIconStyle.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.CardHover),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    IconTheme.icon(style, IconKey.SCHEDULE),
                    contentDescription = null,
                    tint = IconTheme.tint(style, IconKey.SCHEDULE, colors.Accent),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.subject,
                    color = colors.TextPrimary,
                    style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDaysSummary(entry.days, strings),
                    color = colors.TextSecondary,
                    style = ScTextStyles.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ScSwitch(on = entry.enabled, onToggle = onToggleEnabled)
        }
        // Start time · study duration.
        Text(
            "${formatStudyClock(entry.startMinutes)} · ${strings.studyDurationText(entry.durationMinutes)}",
            color = colors.TextPrimary,
            style = ScTextStyles.Body,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        // Reminder line — only when a reminder is configured.
        if (entry.reminderMinutesBefore != null) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = colors.Accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "${strings.studyScheduleReminder}: ${strings.studyScheduleReminderLabel(entry.reminderMinutesBefore)}",
                    color = colors.TextSecondary,
                    style = ScTextStyles.Caption,
                )
            }
        }
        // Actions — Edit + Delete.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) {
                Text(strings.studyScheduleEdit, color = colors.Accent)
            }
            TextButton(onClick = onDelete) {
                Text(strings.webDelete, color = colors.Danger)
            }
        }
    }
}

/** Subtle empty state shown when no schedules exist yet. */
@Composable
private fun ScheduleEmptyState() {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.CardHover)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = colors.TextDisabled,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            strings.studyScheduleEmptyTitle,
            color = colors.TextSecondary,
            style = ScTextStyles.BodySemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            strings.studyScheduleEmptyDesc,
            color = colors.TextSecondary,
            style = ScTextStyles.Caption,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Add / Edit Study Schedule — every field belongs to THIS schedule only:
 * subject, selected days, start time, study duration (presets + Custom) and
 * reminder (No Reminder / presets / Custom). The reminder clock time is
 * always derived (start − reminder), never entered manually.
 */
@Composable
fun StudyScheduleEditScreen(
    existing: StudyScheduleEntry?,
    onSave: (subject: String, days: Set<StudyDay>, startMinutes: Int, durationMinutes: Int, reminderMinutesBefore: Int?) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    var subject by remember { mutableStateOf(existing?.subject ?: "") }
    var selectedDays by remember { mutableStateOf(existing?.days ?: emptySet()) }
    var startMinutes by remember { mutableStateOf(existing?.startMinutes ?: 9 * 60) }
    var durationMinutes by remember { mutableStateOf(existing?.durationMinutes ?: 45) }
    var reminderMinutesBefore by remember { mutableStateOf(existing?.reminderMinutesBefore) }
    var subjectError by remember { mutableStateOf<String?>(null) }
    var daysError by remember { mutableStateOf<String?>(null) }
    var subjectFocused by remember { mutableStateOf(false) }
    var startPickerOpen by remember { mutableStateOf(false) }
    var durationPickerOpen by remember { mutableStateOf(false) }
    var reminderPickerOpen by remember { mutableStateOf(false) }

    val inputShape = RoundedCornerShape(14.dp)

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(
            title = if (existing == null) strings.studyScheduleNewTitle else strings.studyScheduleEditTitle,
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Subject ----
            SectionTitle(strings.studyScheduleSubject)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(inputShape)
                    .background(colors.CardHover, inputShape)
                    .border(
                        1.dp,
                        when {
                            subjectError != null -> colors.Danger
                            subjectFocused -> colors.Accent
                            else -> colors.Divider
                        },
                        inputShape,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = subject,
                    onValueChange = {
                        subject = it
                        subjectError = null
                    },
                    textStyle = ScTextStyles.Body.copy(color = colors.TextPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { subjectFocused = it.isFocused },
                    decorationBox = { inner ->
                        if (subject.isEmpty()) {
                            Text(strings.studyScheduleSubjectPlaceholder, color = colors.TextDisabled, style = ScTextStyles.Body)
                        }
                        inner()
                    },
                )
            }
            if (subjectError != null) {
                Text(subjectError!!, color = colors.Danger, style = ScTextStyles.Caption)
            }

            // ---- Days ----
            SectionTitle(strings.studyScheduleDays)
            DayChips(selected = selectedDays, onToggle = { day ->
                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                daysError = null
            })
            if (daysError != null) {
                Text(daysError!!, color = colors.Danger, style = ScTextStyles.Caption)
            }

            // ---- Start time · duration · reminder ----
            ScheduleFieldRow(
                title = strings.studyScheduleStart,
                value = formatStudyClock(startMinutes),
                onClick = { startPickerOpen = true },
            )
            ScheduleFieldRow(
                title = strings.studyDuration,
                value = strings.studyDurationText(durationMinutes),
                onClick = { durationPickerOpen = true },
            )
            ScheduleFieldRow(
                title = strings.studyScheduleReminder,
                value = reminderMinutesBefore?.let { strings.studyScheduleReminderLabel(it) } ?: strings.studyScheduleReminderNone,
                onClick = { reminderPickerOpen = true },
            )

            Spacer(Modifier.height(4.dp))
            ScButton(
                label = strings.studyDurationSave,
                onClick = {
                    val s = subject.trim()
                    if (s.isBlank()) {
                        subjectError = strings.studyScheduleSubjectRequired
                        return@ScButton
                    }
                    if (selectedDays.isEmpty()) {
                        daysError = strings.studyScheduleDaysRequired
                        return@ScButton
                    }
                    onSave(s, selectedDays, startMinutes, durationMinutes, reminderMinutesBefore)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // ---- Start Time — 12-hour wheel clock (Hour + Minute + AM/PM wheels).
    //      The end time is never picked manually: it derives automatically
    //      from Start Time + Study Duration. ----
    if (startPickerOpen) {
        ScClockWheelDialog(
            title = strings.studyScheduleStart,
            initialMinutesOfDay = startMinutes,
            onConfirm = { startMinutes = it; startPickerOpen = false },
            onCancel = { startPickerOpen = false },
        )
    }

    // ---- Study Duration — the shared Hours + Minutes wheel selector,
    //      prefilled with the current value (presets are wheel positions). ----
    if (durationPickerOpen) {
        ScDurationWheelDialog(
            title = strings.studyDuration,
            initialMinutes = durationMinutes,
            valueLabel = strings::studyDurationText,
            onConfirm = { durationMinutes = it; durationPickerOpen = false },
            onCancel = { durationPickerOpen = false },
        )
    }

    // ---- Reminder — the shared Hours + Minutes wheel selector, prefilled
    //      with the current lead time, plus a "No Reminder" clear action
    //      (null = no reminder). ----
    if (reminderPickerOpen) {
        ScDurationWheelDialog(
            title = strings.studyScheduleReminder,
            initialMinutes = reminderMinutesBefore ?: 15,
            valueLabel = strings::studyScheduleReminderLabel,
            clearLabel = strings.studyScheduleReminderNone,
            onClear = { reminderMinutesBefore = null; reminderPickerOpen = false },
            onConfirm = { reminderMinutesBefore = it; reminderPickerOpen = false },
            onCancel = { reminderPickerOpen = false },
        )
    }

}

/** Settings-style row: label + value + chevron, opens a picker on tap. */
@Composable
private fun ScheduleFieldRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = colors.TextSecondary,
            style = ScTextStyles.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.TextDisabled,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Multi-select weekday chips (Mon–Sun) — wraps on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(
    selected: Set<StudyDay>,
    onToggle: (StudyDay) -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudyDaysInOrder.forEach { day ->
            val isSelected = day in selected
            // STRONG selected state: the ENTIRE chip fills with the ShortsCap
            // green (the same Success green Study Mode uses for its active
            // state), with white text that stays readable. Unselected chips
            // keep the neutral card-hover style — never green. The state is a
            // simple toggle (tap again to deselect) and stays until then.
            val chipBg by animateColorAsState(
                if (isSelected) colors.Success else colors.CardHover,
                label = "dayChipBg",
            )
            val chipText by animateColorAsState(
                if (isSelected) Color.White else colors.TextPrimary,
                label = "dayChipText",
            )
            Row(
                modifier = Modifier
                    // Soft glow behind the selected day — subtle emphasis.
                    .then(if (isSelected) Modifier.shadow(4.dp, RoundedCornerShape(999.dp), clip = false) else Modifier)
                    .clip(RoundedCornerShape(999.dp))
                    .background(chipBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onToggle(day) },
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    strings.studyDayShort(day),
                    color = chipText,
                    style = ScTextStyles.BodySemiBold.copy(fontSize = 13.sp),
                )
            }
        }
    }
}


/**
 * Compact weekday summary with contiguous runs collapsed, e.g.
 * {MON..FRI} → \"Mon – Fri\", {MON,WED,FRI} → \"Mon, Wed, Fri\".
 */
private fun formatDaysSummary(days: Set<StudyDay>, strings: AppStrings): String {
    if (days.isEmpty()) return ""
    val sorted = StudyDaysInOrder.filter { it in days }
    val parts = mutableListOf<String>()
    var runStart = 0
    while (runStart < sorted.size) {
        var runEnd = runStart
        while (
            runEnd + 1 < sorted.size &&
            StudyDaysInOrder.indexOf(sorted[runEnd + 1]) == StudyDaysInOrder.indexOf(sorted[runEnd]) + 1
        ) {
            runEnd++
        }
        parts += if (runEnd > runStart) {
            "${strings.studyDayShort(sorted[runStart])} – ${strings.studyDayShort(sorted[runEnd])}"
        } else {
            strings.studyDayShort(sorted[runStart])
        }
        runStart = runEnd + 1
    }
    return parts.joinToString(", ")
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
