package com.shortscap.app.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.study.DeviceSoundModeResult
import com.shortscap.app.study.FocusPasscodeIcon
import com.shortscap.app.study.StudyModeSettings
import com.shortscap.app.study.StudySoundMode
import com.shortscap.app.study.StudySummary
import com.shortscap.app.study.formatPasscodeSetAt
import com.shortscap.app.study.formatPasscodeSetOn
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
 *   STATUS     — Active / Inactive + the ON/OFF activation toggle (the single
 *                control for starting Study Mode — turning it ON opens a
 *                Start confirmation first). While a session runs it shows the
 *                live countdown.
 *   SESSION    — Live countdown card shown ONLY while a session is running;
 *                there is no Start button (the Status toggle opens the Start
 *                confirmation), and the only way to end early is the Exit
 *                Passcode.
 *   SETTINGS   — Study Duration, Break Reminder, Break Duration, Sound Mode.
 *   EXIT PASSCODE   — Exit Passcode (Not Set / Set + device date-time; the
 *                      ONLY way to end an active session early — via the
 *                      verify screen).
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
    // Exit Passcode — set/status shown in the Exit Passcode section; while a
    // session is active the ONLY way to end early is the passcode verification
    // screen (the subtle exit below the countdown).
    focusPasscodeSet: Boolean,
    /** Device wall-clock millis when the Exit Passcode was set (shown as "Set on/at"). */
    focusPasscodeSetAtMillis: Long,
    onStartSession: () -> Unit,
    onSetStudyDuration: (Int) -> Unit,
    onSetStudyBreakReminder: (Boolean) -> Unit,
    onSetStudyBreakDuration: (Int) -> Unit,
    onSetStudySoundMode: (StudySoundMode) -> DeviceSoundModeResult,
    onOpenSoundModeAccessSettings: () -> Unit,
    onSetStudyScheduleEnabled: (Boolean) -> Unit,
    onSetStudyScheduleStart: (Int) -> Unit,
    onSetStudyScheduleEnd: (Int) -> Unit,
    onOpenAllowed: () -> Unit,
    onOpenFocusPasscodeSetup: () -> Unit,
    onOpenFocusPasscodeVerify: () -> Unit,
    onOpenFocusPasscodeStatus: () -> Unit,
    onDeleteFocusPasscode: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var startDialogOpen by remember { mutableStateOf(false) }
    var stopDialogOpen by remember { mutableStateOf(false) }
    var soundAccessDialogOpen by remember { mutableStateOf(false) }
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
                // The toggle is the SINGLE activation control: turning it ON
                // opens the Start confirmation dialog (Study Mode starts only
                // after the user confirms); while active the only way to end
                // early is the Exit Passcode verification (never deactivates
                // directly).
                onToggle = {
                    if (studyModeActive) {
                        if (focusPasscodeSet) onOpenFocusPasscodeVerify()
                        else onOpenFocusPasscodeSetup()
                    } else {
                        startDialogOpen = true
                    }
                },
            )

            // ---- Session (only while a session is running — the Status
            //      toggle above is the single activation control; there is no
            //      separate Start button) ----
            if (studyModeActive) {
                SectionTitle(strings.studySessionSection)
                // The whole active session card is TAPPABLE: it opens the
                // "Stop Study Mode?" confirmation (never stops directly) whose
                // confirm leads to the SHARED Exit Passcode verification.
                ActiveSessionCard(
                    remainingMillis = studyRemainingMillis,
                    totalMillis = studyTotalMillis,
                    passcodeProtected = focusPasscodeSet,
                    onStop = { stopDialogOpen = true },
                )
                // Subtle protected exit — deliberately NOT dominant. Same
                // "Stop Study Mode?" confirmation as tapping the card.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { stopDialogOpen = true }) {
                        Icon(FocusPasscodeIcon, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.focusPasscodeVerifyButton, color = colors.TextSecondary, style = ScTextStyles.Caption)
                    }
                }
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

            // ---- Exit Passcode (Study Mode protection) ----
            SectionTitle(strings.studyFocusProtection)
            if (focusPasscodeSet) {
                // Set state — green "Passcode Set" status + the device
                // date/time it was set, plus a three-dot (⋮) menu to delete
                // the configuration. Tapping the card opens the passcode
                // STATUS screen (never an "enter your password" field; the
                // passcode is not displayed).
                ScPremiumNavCard(
                    iconKey = IconKey.FOCUS_PASSCODE,
                    title = strings.focusPasscodeTitle,
                    subtitle = "${strings.focusPasscodeSetOn(formatPasscodeSetOn(focusPasscodeSetAtMillis))}\n" +
                        strings.focusPasscodeSetAt(formatPasscodeSetAt(focusPasscodeSetAtMillis)),
                    onClick = onOpenFocusPasscodeStatus,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                strings.focusPasscodeSetStatus,
                                color = colors.Success,
                                style = ScTextStyles.BodySemiBold,
                                maxLines = 1,
                            )
                            // Three-dot menu — the ONLY way to remove the saved
                            // Exit Passcode configuration (returns to Not Set).
                            ExitPasscodeOverflowMenu(onDelete = onDeleteFocusPasscode)
                        }
                    },
                )
            } else {
                // Not set — neutral state; tapping opens the create flow.
                ScPremiumNavCard(
                    iconKey = IconKey.FOCUS_PASSCODE,
                    title = strings.focusPasscodeTitle,
                    subtitle = strings.focusPasscodeNotSet,
                    onClick = onOpenFocusPasscodeSetup,
                )
            }

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

    // ---- "Stop Study Mode?" confirmation (active session) — opens the
    //      SHARED Exit Passcode verification; without a passcode set the
    //      user is routed to first-time setup. Study Mode itself is only ended
    //      after the passcode is verified (or 00:00 reached naturally). ----
    if (stopDialogOpen) {
        AlertDialog(
            onDismissRequest = { stopDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.studyStopTitle) },
            text = { Text(strings.studyStopMessage, style = ScTextStyles.Body) },
            confirmButton = {
                TextButton(onClick = {
                    stopDialogOpen = false
                    if (focusPasscodeSet) onOpenFocusPasscodeVerify()
                    else onOpenFocusPasscodeSetup()
                }) {
                    Text(strings.studyStopAction, color = colors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { stopDialogOpen = false }) {
                    Text(strings.cancel, color = colors.TextSecondary)
                }
            },
        )
    }

    // ---- Sound Mode: system audio access required — explains why and offers
    //      "Open Settings" (Notification Policy Access). The selection is NOT
    //      applied until Android confirms the access; no false success. ----
    if (soundAccessDialogOpen) {
        AlertDialog(
            onDismissRequest = { soundAccessDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.soundModeAccessRequiredTitle) },
            text = { Text(strings.soundModeAccessRequiredDesc, style = ScTextStyles.Body) },
            confirmButton = {
                TextButton(onClick = {
                    soundAccessDialogOpen = false
                    onOpenSoundModeAccessSettings()
                }) {
                    Text(strings.soundModeOpenSettings, color = colors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { soundAccessDialogOpen = false }) {
                    Text(strings.cancel, color = colors.TextSecondary)
                }
            },
        )
    }

    // ---- Study Mode activation confirmation — the toggle only opens this
    //      dialog; Study Mode actually starts AFTER the user confirms. Cancel /
    //      back / outside-tap simply close it (the toggle stays OFF because it
    //      is bound to the real session state, which never changed). ----
    if (startDialogOpen) {
        Dialog(
            onDismissRequest = { startDialogOpen = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            StudyStartConfirmDialog(
                durationMinutes = settings.studyDurationMinutes,
                onConfirm = {
                    startDialogOpen = false
                    onStartSession()
                },
                onCancel = { startDialogOpen = false },
            )
        }
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
        onSelect = { index ->
            soundDialogOpen = false
            when (onSetStudySoundMode(StudySoundMode.entries[index])) {
                DeviceSoundModeResult.APPLIED -> Unit // UI updates via the verified Android state
                DeviceSoundModeResult.POLICY_ACCESS_REQUIRED -> soundAccessDialogOpen = true
                DeviceSoundModeResult.FAILED -> Unit // ViewModel already toasted the failure
            }
        },
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

/**
 * Muted brick red used for the Study Mode INACTIVE state (status text, icon
 * tile and toggle track). Deliberately NOT the bright theme Danger red — a
 * professional, subdued warning tone that fits the dark theme, stays legible
 * against the card background, and clearly contrasts with the Active green.
 */
private val StudyInactiveRed = Color(0xFF9A4A4A)

/**
 * Premium Study Mode activation confirmation — shown when the user turns the
 * toggle ON. Study Mode starts ONLY when "Start Study" (the green primary
 * action) is confirmed; Cancel / back / outside-tap close it without starting
 * anything (the toggle stays OFF because it is bound to the real session
 * state). The duration row always reflects the selected Study Duration.
 */
@Composable
private fun StudyStartConfirmDialog(
    durationMinutes: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        shape = shape,
        color = colors.Card,
        shadowElevation = 24.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Small study icon tile — "Study Mode is about to start."
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = colors.Accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                strings.studyStartConfirmTitle,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 18.sp),
                textAlign = TextAlign.Center,
            )

            // Selected duration — always matches the current Study Duration.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.CardHover)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.studyDurationLabel, color = colors.TextSecondary, style = ScTextStyles.Body)
                Text(strings.studyDurationText(durationMinutes), color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
            }

            Text(
                strings.studyStartConfirmRestrictions,
                color = colors.TextSecondary,
                style = ScTextStyles.Caption,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            // Actions — Cancel (secondary) on the LEFT, Start Study (primary
            // green) on the RIGHT.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StudyDialogButton(
                    label = strings.cancel,
                    variant = StudyDialogButtonVariant.SECONDARY,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                StudyDialogButton(
                    label = strings.studyStartConfirmStart,
                    variant = StudyDialogButtonVariant.PRIMARY,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private enum class StudyDialogButtonVariant { PRIMARY, SECONDARY }

/**
 * Polished dialog button — rounded, easy to tap, with subtle press feedback
 * (scale + a soft background shift on the secondary button).
 */
@Composable
private fun StudyDialogButton(
    label: String,
    variant: StudyDialogButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "studyDialogBtnScale")
    val shape = RoundedCornerShape(14.dp)
    val bg = when (variant) {
        // Primary = the positive/confirm action — professional theme green.
        StudyDialogButtonVariant.PRIMARY -> colors.Success
        StudyDialogButtonVariant.SECONDARY -> if (pressed) colors.CardHover else colors.Card
    }
    val fg = if (variant == StudyDialogButtonVariant.PRIMARY) Color.White else colors.TextPrimary
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg, shape)
            .then(if (variant == StudyDialogButtonVariant.SECONDARY) Modifier.border(1.dp, colors.Divider, shape) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, style = ScTextStyles.ButtonLabel, maxLines = 1)
    }
}

/**
 * Status header — Active/Inactive + the ON/OFF activation toggle. The switch is
 * the single source of truth for Study Mode's active/inactive UI state: it is
 * bound to the real session state, so it can never flip OFF before the Exit
 * Passcode is verified (or the countdown reaches 00:00).
 */
@Composable
private fun StudyStatusCard(
    active: Boolean,
    remainingText: String,
    onToggle: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(22.dp)
    val statusColor = if (active) colors.Success else StudyInactiveRed
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
                .background(statusColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (active) strings.studyStatusActive else strings.studyStatusInactive,
                color = statusColor,
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
        StudyModeToggle(on = active, onToggle = onToggle)
    }
}

/**
 * Polished Android-style switch with a clear thumb/track (same compact 42x24
 * footprint as [com.shortscap.app.components.ScSwitch]). The track color
 * animates smoothly between professional green (Active) and the muted inactive
 * red (Inactive); the thumb stays white for a clean, balanced look.
 */
@Composable
private fun StudyModeToggle(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    val knobOffset by animateDpAsState(if (on) 21.dp else 3.dp, label = "studyToggleKnob")
    val trackColor by animateColorAsState(
        if (on) colors.Success else StudyInactiveRed,
        label = "studyToggleTrack",
    )
    Box(
        modifier = modifier
            .width(42.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
    ) {
        Box(
            Modifier
                .padding(top = 3.dp, start = knobOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Active session card — big countdown, progress, lock indicator + note. The
 *  whole card is tappable ([onStop] → "Stop Study Mode?" confirmation). */
@Composable
private fun ActiveSessionCard(
    remainingMillis: Long,
    totalMillis: Long,
    passcodeProtected: Boolean,
    onStop: (() -> Unit)? = null,
) {
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onStop != null,
                onClick = { onStop?.invoke() },
            )
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
        if (passcodeProtected) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(FocusPasscodeIcon, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(14.dp))
                Text(
                    strings.focusPasscodeLockedNote,
                    color = colors.TextSecondary,
                    style = ScTextStyles.Caption,
                    textAlign = TextAlign.Center,
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
