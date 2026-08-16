package com.shortscap.app.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.shorts.DEFAULT_LIMIT_UPPER_BOUND
import com.shortscap.app.shorts.LimitInputError
import com.shortscap.app.shorts.LimitInputResult
import com.shortscap.app.shorts.ShortsControlEngine
import com.shortscap.app.shorts.ShortsControlState
import com.shortscap.app.shorts.ShortsControlSyncer
import com.shortscap.app.shorts.ShortsLimitPageState
import com.shortscap.app.shorts.ShortsSyncStatus
import com.shortscap.app.shorts.deriveLimitPageState
import com.shortscap.app.shorts.limitProgressFraction
import com.shortscap.app.shorts.parseLimitInput
import com.shortscap.app.shorts.remainingCountdownHms
import com.shortscap.app.shorts.remainingHoursMinutes
import com.shortscap.app.shorts.timeProgressFraction
import com.shortscap.app.sync.SyncCoordinator
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScColors
import com.shortscap.app.theme.ScTextStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shorts Limit — the FINAL 24-hour limit control page (Settings → Short
 * Control → Shorts Limit).
 *
 * FINAL PRODUCT RULE: Shorts Limit is NOT an optional on/off feature. The
 * flow is explicit: CONFIGURE → SAVE → READY TO ACTIVATE → user presses the
 * bottom-anchored ACTIVE button → 24-hour cycle starts → limit is LOCKED
 * until the cycle expires. There is NO enable/disable toggle and NO password.
 *
 * The page never counts Shorts and never owns a cycle: it renders the
 * AUTHORITATIVE [ShortsControlState] from [ShortsControlEngine] and persists
 * every action through the engine's Room-backed store, so count / limit /
 * cycle survive app restart, process death and force-stop.
 *
 * The ACTIVE button is the primary CTA, anchored at the bottom of the page
 * and always visible: green + enabled in READY / EXPIRED (something to
 * start), grey + disabled while a cycle is running (ACTIVE / WARNING /
 * LIMIT_REACHED) so it can never restart or duplicate the cycle. Pressing it
 * asks one confirmation ("Start your Shorts limit for the next 24 hours?"),
 * then starts the existing cycle through the engine.
 *
 * Two DISTINCT progress values (never combined):
 *  - TIME progress — the circular 24-hour clock: remaining / 24h, shown as a
 *    live HH:MM:SS countdown derived from cycleExpiresAt - now.
 *  - SHORTS USAGE progress — currentCount / limitCount, shown separately.
 *
 * The 24-hour edit lock is enforced by the engine ([ShortsControlEngine.isLimitLocked]);
 * debug builds get the SAFE development-only test seam (edit during an active
 * cycle) while release/production builds enforce the lock — never exposed as
 * a UI toggle, never stored as a preference.
 */
@Composable
fun ShortsLimitScreen(
    onBack: () -> Unit,
    engine: ShortsControlEngine = ShortsControlEngine.shared,
    syncer: ShortsControlSyncer? = SyncCoordinator.shortsControlSync,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    // Live clock — drives the visible countdown. One lightweight tick per
    // second while the page is on screen; the LaunchedEffect is cancelled
    // with the composition, so there is no background loop.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    // Authoritative state re-derived each tick (expiry is timestamp-driven).
    val state = remember(now) { engine.currentState(now) }
    val pageState = deriveLimitPageState(state)
    var syncStatus by remember { mutableStateOf(ShortsSyncStatus.IDLE) }

    var confirmOpen by remember { mutableStateOf(false) }
    var pendingLimit by remember { mutableStateOf<Int?>(null) }
    var editOpen by remember { mutableStateOf(false) }
    var activateConfirmOpen by remember { mutableStateOf(false) }

    fun pushSync(block: suspend ShortsControlSyncer.() -> ShortsSyncStatus) {
        val s = syncer ?: return
        scope.launch {
            syncStatus = ShortsSyncStatus.SYNCING
            syncStatus = s.block()
        }
    }

    // ACTIVE is the single action that starts the 24-hour cycle. Enabled only
    // when there is something to start (READY after saving, or EXPIRED for
    // the next window); grey + disabled while a cycle is running (ACTIVE /
    // WARNING / LIMIT_REACHED) so it can never be re-pressed to restart or
    // duplicate the cycle. During first-time setup no limit is saved yet, so
    // the button stays disabled until "Set Shorts Limit" confirms one.
    val canActivate = when (pageState) {
        ShortsLimitPageState.READY_TO_ACTIVATE,
        ShortsLimitPageState.EXPIRED,
        -> true
        else -> false
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.shortsLimitTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (pageState) {
                ShortsLimitPageState.LOADING -> {
                    Text(strings.loading, color = colors.TextSecondary, style = ScTextStyles.Body)
                }

                // Never configured / disabled -> first-time setup.
                ShortsLimitPageState.NO_LIMIT_CONFIGURED,
                ShortsLimitPageState.LIMIT_SETUP,
                ShortsLimitPageState.DISABLED,
                -> {
                    SetupView(
                        onSetLimit = { limit ->
                            pendingLimit = limit
                            confirmOpen = true
                        },
                    )
                }

                // Limit saved but NOT activated — READY state: timer not
                // started, limit not locked, editing available.
                ShortsLimitPageState.READY_TO_ACTIVATE,
                ShortsLimitPageState.EXPIRED,
                -> {
                    if (pageState == ShortsLimitPageState.EXPIRED) {
                        NoticeBanner(text = strings.shortsLimitExpiredNotice, color = colors.Warning)
                    }
                    ReadyToActivateView(
                        state = state,
                        onEdit = { editOpen = true },
                    )
                }

                // Cycle running: 24-hour countdown + locked limit.
                ShortsLimitPageState.ACTIVE,
                ShortsLimitPageState.WARNING,
                ShortsLimitPageState.LIMIT_REACHED,
                -> {
                    ActiveView(
                        state = state,
                        pageState = pageState,
                        isLocked = engine.isLimitLocked(),
                        onEdit = { editOpen = true },
                    )
                }
            }

            // Sync status — the durable local state stays authoritative; the
            // banner only tells the user the mirror push did not arrive.
            when (syncStatus) {
                ShortsSyncStatus.OFFLINE -> NoticeBanner(text = strings.shortsLimitOffline, color = colors.Warning)
                ShortsSyncStatus.ERROR -> NoticeBanner(text = strings.shortsLimitSyncError, color = colors.Danger)
                else -> Unit
            }
        }

        // Bottom-anchored ACTIVE — the primary CTA, always visible so the
        // cycle state stays legible (green + enabled to start, grey +
        // disabled while the 24-hour cycle is running).
        ActivateBar(
            enabled = canActivate,
            onClick = { activateConfirmOpen = true },
        )
    }

    // First save: confirmation — the limit is CONFIGURED only; the 24-hour
    // cycle starts when the user presses ACTIVE on the READY page.
    if (confirmOpen && pendingLimit != null) {
        SaveLimitConfirmDialog(
            onSave = {
                confirmOpen = false
                engine.setLimit(pendingLimit!!)
                pushSync { syncEditLimit(pendingLimit!!) }
            },
            onDismiss = { confirmOpen = false },
        )
    }

    // ACTIVE confirmation — one dialog, then the existing cycle starts.
    if (activateConfirmOpen) {
        ActivateConfirmDialog(
            onActivate = {
                activateConfirmOpen = false
                engine.activate()
                pushSync { syncActivate(state.limitCount) }
            },
            onDismiss = { activateConfirmOpen = false },
        )
    }

    if (editOpen) {
        EditLimitDialog(
            current = state.limitCount.takeIf { it > 0 } ?: 200,
            locked = engine.isLimitLocked(),
            onSave = { limit ->
                editOpen = false
                engine.setLimit(limit)
                pushSync { syncEditLimit(limit) }
            },
            onDismiss = { editOpen = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Setup (no active cycle): 24:00:00 timer ring + presets + Custom + confirm
// ---------------------------------------------------------------------------

@Composable
private fun SetupView(
    onSetLimit: (Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalScColors.current
    val presets = listOf(50, 100, 150, 200, 300, 500)
    var selected by remember { mutableStateOf<Int?>(200) }
    var customMode by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("200") }
    var error by remember { mutableStateOf<String?>(null) }

    // The next cycle preview — a full 24:00:00 window that will begin when
    // the user saves their limit.
    CycleTimerRing(
        value = "24:00:00",
        progress = 1f,
        label = strings.shortsLimitCycle24Hour,
        contentDesc = strings.shortsCircularProgress,
    )

    SectionTitle(strings.shortsLimitSetButton)

    // Preset chips + Custom.
    Text(strings.shortsLimitPresets, color = colors.TextSecondary, style = ScTextStyles.Label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            PresetChip(
                label = "$preset",
                selected = !customMode && selected == preset,
                onClick = {
                    customMode = false
                    selected = preset
                    text = preset.toString()
                    error = null
                },
            )
        }
        PresetChip(
            label = strings.shortsLimitCustom,
            selected = customMode,
            onClick = {
                customMode = true
                error = null
            },
        )
    }

    // Custom input row (steppers + numeric keyboard + validation).
    if (customMode) {
        LimitInputRow(
            text = text,
            onTextChange = { new ->
                text = new.filter { it.isDigit() }.take(6)
                error = null
            },
            onAdjust = { delta ->
                val base = (parseLimitInput(text) as? LimitInputResult.Valid)?.value ?: 200
                text = (base + delta).coerceIn(1, DEFAULT_LIMIT_UPPER_BOUND).toString()
                error = null
            },
            unitLabel = strings.shortsLimitUnit,
            inputLabel = strings.shortsLimitInputLabel,
            incrementLabel = strings.shortsLimitStepperIncrement,
            decrementLabel = strings.shortsLimitStepperDecrement,
        )
        error?.let {
            Text(it, color = colors.Danger, style = ScTextStyles.Caption, modifier = Modifier.fillMaxWidth())
        }
    }

    // Primary CTA — validates, then asks for confirmation.
    PrimaryButton(
        label = strings.shortsLimitSetButton,
        enabled = selected != null,
        onClick = {
            when (val result = if (customMode) parseLimitInput(text) else LimitInputResult.Valid(selected!!)) {
                is LimitInputResult.Valid -> onSetLimit(result.value)
                LimitInputResult.Empty -> error = strings.shortsLimitRequired
                is LimitInputResult.Invalid -> error = when (result.reason) {
                    LimitInputError.NOT_A_NUMBER -> strings.shortsLimitInvalidNumber
                    LimitInputError.NOT_POSITIVE -> strings.shortsLimitPositive
                    LimitInputError.TOO_LARGE -> strings.shortsLimitTooLarge(DEFAULT_LIMIT_UPPER_BOUND)
                }
            }
        },
    )
}

/**
 * The bottom-anchored ACTIVE bar — the primary CTA of the page. Always
 * visible so the cycle state stays legible: green + enabled when there is
 * something to start (READY / EXPIRED), grey + disabled while a 24-hour
 * cycle is running (ACTIVE / WARNING / LIMIT_REACHED) so it can never be
 * re-pressed to restart or duplicate the cycle.
 */
@Composable
private fun ActivateBar(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.Bg)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        PrimaryButton(
            label = strings.shortsLimitActivateNow,
            enabled = enabled,
            onClick = onClick,
        )
    }
}

/** ACTIVE confirmation — one dialog, then the existing cycle starts. */
@Composable
private fun ActivateConfirmDialog(
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextPrimary,
        title = { Text(strings.shortsLimitActivateConfirmTitle) },
        confirmButton = {
            TextButton(onClick = onActivate) {
                Text(strings.shortsLimitActivateConfirmAction, color = colors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = colors.TextSecondary)
            }
        },
    )
}

/** First-save confirmation — saving CONFIGURES the limit; ACTIVE starts the cycle. */
@Composable
private fun SaveLimitConfirmDialog(
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextPrimary,
        title = { Text(strings.shortsLimitConfirmTitle) },
        text = {
            Text(strings.shortsLimitConfirmDesc, color = colors.TextSecondary, style = ScTextStyles.Body)
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(strings.shortsLimitSaveLimit, color = colors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = colors.TextSecondary)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// READY_TO_ACTIVATE — limit saved, cycle NOT started
// ---------------------------------------------------------------------------

@Composable
private fun ReadyToActivateView(
    state: ShortsControlState,
    onEdit: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    // The saved limit, clearly displayed (the bottom ACTIVE bar starts it).
    SectionTitle(strings.shortsLimitYourLimit)
    LimitCard(
        state = state,
        locked = false,
        onEdit = onEdit,
        strings = strings,
        colors = colors,
    )

    // Status + timer: READY TO ACTIVATE / NOT STARTED (compact two-stat row).
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StatBlock(label = strings.shortsLimitState, value = strings.shortsLimitReadyToActivate, colors = colors)
        StatBlock(label = strings.shortsLimitCycle24Hour, value = strings.shortsLimitTimerNotStarted, colors = colors)
    }
}

// ---------------------------------------------------------------------------
// Active cycle page
// ---------------------------------------------------------------------------

@Composable
private fun ActiveView(
    state: ShortsControlState,
    pageState: ShortsLimitPageState,
    isLocked: Boolean,
    onEdit: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    // Warning / limit-reached banners above the cycle.
    when (pageState) {
        ShortsLimitPageState.WARNING -> NoticeBanner(text = strings.shortsLimitWarningDesc, color = colors.Warning)
        ShortsLimitPageState.LIMIT_REACHED -> NoticeBanner(text = strings.shortsLimitReachedDesc, color = colors.Danger)
        else -> Unit
    }

    // ---- 24-HOUR CYCLE — the TIME clock (remaining / 24h). ----
    SectionTitle(strings.shortsLimitCycle24Hour)
    CycleTimerRing(
        value = remainingCountdownHms(state.remainingCycleMillis),
        progress = timeProgressFraction(state.remainingCycleMillis, ShortsControlEngine.CYCLE_DURATION_MILLIS),
        label = strings.shortsLimitCycle24Hour,
        contentDesc = "${strings.shortsLimitCycle24Hour} ${remainingCountdownHms(state.remainingCycleMillis)}",
    )
    val (hours, minutes) = remainingHoursMinutes(state.remainingCycleMillis)
    Text(
        text = strings.shortsLimitCycleRemainingFormat.format(hours, minutes),
        color = colors.TextSecondary,
        style = ScTextStyles.Body,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )

    // ---- SHORTS USAGE — the count progress (current / limit). ----
    SectionTitle(strings.shortsLimitUsageSection)
    UsageCard(state = state, strings = strings, colors = colors)

    // ---- YOUR LIMIT — saved limit + edit (locked while the cycle runs). ----
    SectionTitle(strings.shortsLimitYourLimit)
    LimitCard(
        state = state,
        locked = isLocked,
        onEdit = onEdit,
        strings = strings,
        colors = colors,
    )
}

/** The active-usage card: current/limit, remaining, status, usage bar. */
@Composable
private fun UsageCard(
    state: ShortsControlState,
    strings: AppStrings,
    colors: ScColors,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = strings.shortsLimitCounterFormat.format(state.currentCount, state.limitCount),
            color = colors.TextPrimary,
            style = ScTextStyles.BigStat,
        )
        // Compact usage progress bar — separate from the 24-hour clock.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.ProgressTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(limitProgressFraction(state.currentCount, state.limitCount))
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StatBlock(label = strings.shortsLimitRemainingShorts, value = "${state.remainingCount}", colors = colors)
            StatBlock(label = strings.shortsLimitState, value = statusLabel(state, strings), colors = colors)
        }
    }
}

/** The saved-limit card: \"200 Shorts\" + Edit Limit (locked message shown). */
@Composable
private fun LimitCard(
    state: ShortsControlState,
    locked: Boolean,
    onEdit: () -> Unit,
    strings: AppStrings,
    colors: ScColors,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = strings.shortsLimitCardDesc },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${state.limitCount} ${strings.shortsLimitUnit}",
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEdit) {
                Text(strings.shortsLimitEdit, color = colors.Accent)
            }
        }
        if (locked) {
            NoticeBanner(text = strings.shortsLimitLockedMessage, color = colors.TextSecondary)
        }
    }
}

/** Edit dialog — presets + Custom, but Save respects the 24-hour lock. */
@Composable
private fun EditLimitDialog(
    current: Int,
    locked: Boolean,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val presets = listOf(50, 100, 150, 200, 300, 500)
    var selected by remember { mutableStateOf<Int?>(current) }
    var customMode by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(current.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextPrimary,
        title = { Text(strings.shortsLimitEdit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (locked) {
                    NoticeBanner(text = strings.shortsLimitLockedMessage, color = colors.TextSecondary)
                }
                Text(strings.shortsLimitPresets, color = colors.TextSecondary, style = ScTextStyles.Label)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        PresetChip(
                            label = "$preset",
                            selected = !customMode && selected == preset,
                            onClick = {
                                customMode = false
                                selected = preset
                                text = preset.toString()
                                error = null
                            },
                        )
                    }
                    PresetChip(
                        label = strings.shortsLimitCustom,
                        selected = customMode,
                        onClick = {
                            customMode = true
                            error = null
                        },
                    )
                }
                if (customMode) {
                    LimitInputRow(
                        text = text,
                        onTextChange = { new ->
                            text = new.filter { it.isDigit() }.take(6)
                            error = null
                        },
                        onAdjust = { delta ->
                            val base = (parseLimitInput(text) as? LimitInputResult.Valid)?.value ?: current
                            text = (base + delta).coerceIn(1, DEFAULT_LIMIT_UPPER_BOUND).toString()
                            error = null
                        },
                        unitLabel = strings.shortsLimitUnit,
                        inputLabel = strings.shortsLimitInputLabel,
                        incrementLabel = strings.shortsLimitStepperIncrement,
                        decrementLabel = strings.shortsLimitStepperDecrement,
                    )
                    error?.let {
                        Text(it, color = colors.Danger, style = ScTextStyles.Caption, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !locked && selected != null,
                onClick = {
                    val result = if (customMode) parseLimitInput(text) else LimitInputResult.Valid(selected!!)
                    when (result) {
                        is LimitInputResult.Valid -> onSave(result.value)
                        LimitInputResult.Empty -> error = strings.shortsLimitRequired
                        is LimitInputResult.Invalid -> error = when (result.reason) {
                            LimitInputError.NOT_A_NUMBER -> strings.shortsLimitInvalidNumber
                            LimitInputError.NOT_POSITIVE -> strings.shortsLimitPositive
                            LimitInputError.TOO_LARGE -> strings.shortsLimitTooLarge(DEFAULT_LIMIT_UPPER_BOUND)
                        }
                    }
                },
            ) {
                Text(strings.shortsLimitSaveLimit, color = if (locked) colors.TextDisabled else colors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = colors.TextSecondary)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

/**
 * The circular 24-hour clock: a smooth ring whose sweep is the TIME fraction
 * (remaining / 24h — full circle at cycle start, depleting to 0 at expiry)
 * with the HH:MM:SS countdown in the center. Completely separate from the
 * Shorts usage fraction.
 */
@Composable
private fun CycleTimerRing(
    value: String,
    progress: Float,
    label: String,
    contentDesc: String,
) {
    val colors = LocalScColors.current
    val ringBrush = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = contentDesc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(148.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 12.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                // Subtle full track so the remaining window is always visible.
                drawArc(
                    color = colors.ProgressTrack,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                if (progress > 0f) {
                    drawArc(
                        brush = ringBrush,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
            Text(value, color = colors.TextPrimary, style = ScTextStyles.StatValue)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = colors.TextSecondary, style = ScTextStyles.SectionTitle, textAlign = TextAlign.Center)
    }
}

/** A rounded preset/custom chip with a clear selected state. */
@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.ChipActiveBg else colors.Card)
            .border(1.dp, if (selected) colors.Accent.copy(alpha = 0.5f) else colors.Divider, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) colors.ChipActiveText else colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
        )
    }
}

/** The numeric limit input: [−] text [+] steppers plus a numeric keyboard. */
@Composable
private fun LimitInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    onAdjust: (Int) -> Unit,
    unitLabel: String,
    inputLabel: String,
    incrementLabel: String,
    decrementLabel: String,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepperButton(icon = Icons.Filled.Remove, label = decrementLabel, onClick = { onAdjust(-10) })
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = inputLabel },
                textStyle = ScTextStyles.StatValue.copy(color = colors.TextPrimary, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
        StepperButton(icon = Icons.Filled.Add, label = incrementLabel, onClick = { onAdjust(10) })
    }
    Text(
        text = unitLabel,
        color = colors.TextSecondary,
        style = ScTextStyles.Label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepperButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalScColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.CardHover)
            .border(1.dp, colors.Divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Filled primary CTA, matching the app's accent language. */
@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalScColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) colors.Accent else colors.CardHover)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else colors.TextDisabled,
            style = ScTextStyles.ButtonLabel,
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, colors: ScColors) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = colors.TextSecondary, style = ScTextStyles.Label)
        Text(value, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp))
    }
}

@Composable
private fun NoticeBanner(text: String, color: Color) {
    val colors = LocalScColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = color, style = ScTextStyles.BodySemiBold)
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

private fun statusLabel(state: ShortsControlState, strings: AppStrings): String = when {
    state.limitReached -> strings.shortsLimitStateLimitReached
    state.warningTriggered -> strings.shortsLimitWarning
    else -> strings.shortsLimitStateActive
}
