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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.network.ApiResult
import com.shortscap.app.network.ShortsPlatformUsageDto
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
import com.shortscap.app.shorts.timeProgressFraction
import com.shortscap.app.sync.SyncCoordinator
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScColors
import com.shortscap.app.theme.ScTextStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Activation green used for BOTH the ACTIVATE button and the confirmation
 * dialog's final Activate action — a single activation color (not blue). */
private val ActivateGreen = Color(0xFF22C55E)

/** Hard minimum Shorts limit — values below this are rejected at input. */
private const val MIN_SHORTS_LIMIT = 50

/**
 * Shorts Limit — the 24-hour Shorts limit control page (Settings → Short
 * Control → Shorts Limit). ONE unified screen, not a wizard:
 *
 *  1. The 24-hour circular T I M E  clock at the top. Before activation it
 *     shows a full 24:00:00 window (the countdown does NOT run); once ACTIVE
 *     it live-updates as the remaining HH:MM:SS derived from the engine's
 *     authoritative expiry timestamp.
 *  2. ONE numeric "Set Shorts Limit" input — no presets, no Custom chip, no
 *     separate "Selected Limit" section, no persistence/duration settings.
 *     Accepts whole numbers from 50 upwards (minimum 50, validated inline).
 *  3. Consumed Shorts + Remaining Shorts, derived from the engine state.
 *  4. A colorful percentage-based Shorts usage progress bar.
 *  5. Platform-wise Shorts usage, from the backend `shorts_usage`
 *     aggregation (real data only, never fabricated; refreshed while
 *     on-screen). Shows "No Shorts usage recorded yet." until the backend
 *     provides data — no demo/sample rows.
 *  6. The green ACTIVATE button — the primary CTA, placed below the Platform
 *     Usage section with normal page margins, well above the system nav bar.
 *
 * Activation: the user enters a limit and presses ACTIVATE; a confirmation
 * dialog explains that this starts a locked 24-hour Shorts limit cycle. On
 * confirmation the limit is applied to the engine, the cycle starts, the
 * countdown/consumed/remaining/progress/platform values begin live-updating,
 * the limit configuration is locked until the cycle ends, and ACTIVATE turns
 * grey + disabled. ACTIVATE can never restart or duplicate a running cycle.
 *
 * The page renders the AUTHORITATIVE [ShortsControlState] from
 * [ShortsControlEngine]; count / limit / cycle survive restart, process death
 * and force-stop.
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
    // second while the page is on screen; cancelled with the composition.
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

    // Unified limit input — single source of the limit the user wants.
    var limitText by remember { mutableStateOf(state.limitCount.takeIf { it > 0 }?.toString() ?: "") }

    // A cycle is RUNNING while ACTIVE / WARNING / LIMIT_REACHED. In that
    // state the configuration is locked, the countdown runs, and ACTIVATE is
    // disabled. READY_TO_ACTIVATE / EXPIRED / unconfigured = nothing running.
    val running = when (pageState) {
        ShortsLimitPageState.ACTIVE,
        ShortsLimitPageState.WARNING,
        ShortsLimitPageState.LIMIT_REACHED,
        -> true
        else -> false
    }

    var activateConfirmOpen by remember { mutableStateOf(false) }

    // Real per-platform usage within the current cycle window — from the
    // backend only. Polled while the page is on screen so consumption,
    // platform rows, consumed/remaining and the progress bar all stay live.
    var platformUsage by remember { mutableStateOf<List<ShortsPlatformUsageDto>>(emptyList()) }
    LaunchedEffect(Unit) {
        val s = syncer ?: return@LaunchedEffect
        while (true) {
            when (val result = s.fetchControl()) {
                is ApiResult.Success -> platformUsage = result.data.platformUsage
                else -> Unit
            }
            delay(15_000L)
        }
    }

    // Platform rows shown on screen: real backend aggregation only — never
    // fabricated, never derived locally. Empty until the backend provides data.

    fun pushSync(block: suspend ShortsControlSyncer.() -> ShortsSyncStatus) {
        val s = syncer ?: return
        scope.launch {
            syncStatus = ShortsSyncStatus.SYNCING
            syncStatus = s.block()
        }
    }

    // Validation of the single numeric input — a whole number from 50 upwards.
    val parsedLimit = parseLimitInput(limitText)
    val limit = (parsedLimit as? LimitInputResult.Valid)?.value ?: 0
    val inputError = when {
        limitText.isEmpty() -> null
        parsedLimit is LimitInputResult.Valid && parsedLimit.value < MIN_SHORTS_LIMIT -> strings.shortsLimitMinimum(MIN_SHORTS_LIMIT)
        parsedLimit is LimitInputResult.Valid -> null
        parsedLimit == LimitInputResult.Empty -> strings.shortsLimitRequired
        parsedLimit is LimitInputResult.Invalid -> when (parsedLimit.reason) {
            LimitInputError.NOT_A_NUMBER -> strings.shortsLimitInvalidNumber
            LimitInputError.NOT_POSITIVE -> strings.shortsLimitPositive
            LimitInputError.TOO_LARGE -> strings.shortsLimitTooLarge(DEFAULT_LIMIT_UPPER_BOUND)
        }
        else -> null
    }

    // Effective limit for consumed/remaining/progress: the running cycle's
    // locked limit, else the entered value (falling back to the saved limit).
    val displayLimit = if (running) state.limitCount else (limit.takeIf { it > 0 } ?: state.limitCount.takeIf { it > 0 } ?: 0)
    val consumed = state.currentCount
    val remaining = (displayLimit - consumed).coerceAtLeast(0)
    val fraction = limitProgressFraction(consumed, displayLimit)

    // ACTIVATE: enabled only when the limit is valid AND at least the 50-Shorts
    // minimum AND no cycle is running; grey + disabled while active so it can
    // never be re-pressed.
    val canActivate = !running && limit >= MIN_SHORTS_LIMIT

    val onActivate: () -> Unit = {
        activateConfirmOpen = false
        val valid = parsedLimit as? LimitInputResult.Valid
        if (valid != null) {
            engine.setLimit(valid.value)
            engine.activate()
            pushSync { syncActivate(valid.value) }
        }
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
            // State banners — warning / limit-reached / expired / sync.
            when (pageState) {
                ShortsLimitPageState.EXPIRED -> NoticeBanner(text = strings.shortsLimitExpiredNotice, color = colors.Warning)
                ShortsLimitPageState.WARNING -> NoticeBanner(text = strings.shortsLimitWarningDesc, color = colors.Warning)
                ShortsLimitPageState.LIMIT_REACHED -> NoticeBanner(text = strings.shortsLimitReachedDesc, color = colors.Danger)
                else -> Unit
            }
            when (syncStatus) {
                ShortsSyncStatus.OFFLINE -> NoticeBanner(text = strings.shortsLimitOffline, color = colors.Warning)
                ShortsSyncStatus.ERROR -> NoticeBanner(text = strings.shortsLimitSyncError, color = colors.Danger)
                else -> Unit
            }

            // 1. 24-hour circular timer — full 24:00:00 before activation,
            // live HH:MM:SS countdown while a cycle is running.
            CycleTimerRing(
                value = if (running) remainingCountdownHms(state.remainingCycleMillis) else "24:00:00",
                progress = if (running) timeProgressFraction(state.remainingCycleMillis, ShortsControlEngine.CYCLE_DURATION_MILLIS) else 1f,
                label = strings.shortsLimitCycle24Hour,
                contentDesc = strings.shortsCircularProgress,
            )

            // 2. ONE numeric input — no presets.
            SectionTitle(strings.shortsLimitSetButton)
            LimitInput(
                text = limitText,
                enabled = !running,
                placeholder = strings.shortsLimitPlaceholder,
                contentDesc = strings.shortsLimitInputLabel,
                onTextChange = { new ->
                    limitText = new.filter { it.isDigit() }.take(6)
                },
            )
            inputError?.let {
                Text(it, color = colors.Danger, style = ScTextStyles.Caption, modifier = Modifier.fillMaxWidth())
            }

            // 3. Consumed / Remaining.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                StatBlock(label = strings.shortsLimitConsumed, value = "$consumed", colors = colors)
                StatBlock(label = strings.shortsLimitRemainingShorts, value = "$remaining", colors = colors)
            }

            // 4. Colorful percentage-based usage progress bar.
            UsageProgressBar(fraction = fraction, strings = strings, colors = colors)

            // 5. Platform-wise Shorts usage — real backend aggregation.
            SectionTitle(strings.shortsLimitPlatformSection)
            PlatformUsageCard(
                usages = platformUsage,
                strings = strings,
                colors = colors,
            )

            // 6. Green ACTIVATE — in-content, immediately below the Platform
            // Usage section, well above the Android system navigation bar.
            PrimaryButton(
                label = strings.shortsLimitActivateNow,
                enabled = canActivate,
                onClick = { activateConfirmOpen = true },
            )
        }
    }

    if (activateConfirmOpen) {
        ActivateConfirmDialog(
            onActivate = onActivate,
            onDismiss = { activateConfirmOpen = false },
        )
    }
}

// ---------------------------------------------------------------------------
// 24-hour circular timer
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
            modifier = Modifier.size(120.dp),
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

// ---------------------------------------------------------------------------
// Single numeric limit input
// ---------------------------------------------------------------------------

/** The ONE compact limit input — a right-sized number field with a
 * placeholder. Not full-width and not a large card; only large enough to
 * enter the number. No presets, no steppers, no Custom chip. */
@Composable
private fun LimitInput(
    text: String,
    enabled: Boolean,
    placeholder: String,
    contentDesc: String,
    onTextChange: (String) -> Unit,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(150.dp)
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .clickable(enabled = enabled) { /* focus via text field */ }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center,
    ) {
        if (text.isEmpty()) {
            Text(
                placeholder,
                color = colors.TextSecondary,
                style = ScTextStyles.Body.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = ScTextStyles.Body.copy(
                color = if (enabled) colors.TextPrimary else colors.TextDisabled,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(colors.Accent),
        )
    }
}

// ---------------------------------------------------------------------------
// ACTIVATE + confirmation
// ---------------------------------------------------------------------------

/** ACTIVATE confirmation — one dialog, then the 24-hour cycle starts. */
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
        text = { Text(strings.shortsLimitActivateConfirmDesc, color = colors.TextSecondary, style = ScTextStyles.Body) },
        confirmButton = {
            TextButton(onClick = onActivate) {
                Text(strings.shortsLimitActivateConfirmAction, color = ActivateGreen)
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
// Consumed / Remaining + usage progress
// ---------------------------------------------------------------------------

/** Colorful percentage-based Shorts usage progress bar (consumed / limit). */
@Composable
private fun UsageProgressBar(fraction: Float, strings: AppStrings, colors: ScColors) {
    val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()
    val tierColor = when {
        fraction >= 1f -> colors.Danger
        fraction >= 0.75f -> colors.Warning
        else -> MaterialTheme.colorScheme.primary
    }
    val fillBrush = Brush.linearGradient(listOf(tierColor, MaterialTheme.colorScheme.secondary))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.shortsLimitUsageSection, color = colors.TextSecondary, style = ScTextStyles.Label)
            Text("$pct%", color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.ProgressTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fillBrush),
            )
        }
    }
}

/** The platform-usage card — REAL per-platform counts + minutes within the
 * current 24-hour cycle (from the backend `shorts_usage` aggregation only;
 * an empty list shows the honest empty state). No platform icons. */
@Composable
private fun PlatformUsageCard(
    usages: List<ShortsPlatformUsageDto>,
    strings: AppStrings,
    colors: ScColors,
) {
    val shape = RoundedCornerShape(22.dp)
    val rows = usages.sortedByDescending { it.shortsCount }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (rows.isEmpty()) {
            Text(strings.shortsLimitPlatformEmpty, color = colors.TextSecondary, style = ScTextStyles.Body)
        } else {
            rows.forEach { usage ->
                val name = platformDisplayName(usage.platform)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = strings.shortsLimitPlatformRowFormat(name, usage.shortsCount, usage.durationSeconds / 60)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        color = colors.TextPrimary,
                        style = ScTextStyles.BodySemiBold.copy(fontSize = 14.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = strings.shortsLimitPlatformRowFormat(name, usage.shortsCount, usage.durationSeconds / 60),
                        color = colors.TextSecondary,
                        style = ScTextStyles.Body,
                    )
                }
            }
        }
    }
}

/** Friendly display label for a backend `platform` literal (ShortPlatform
 * enum name). Falls back to the raw literal — never a fabricated platform. */
private fun platformDisplayName(platform: String?): String = when (platform) {
    "YOUTUBE" -> "YouTube Shorts"
    "INSTAGRAM" -> "Instagram Reels"
    "TIKTOK" -> "TikTok"
    "SNAPCHAT" -> "Snapchat Spotlight"
    "FACEBOOK" -> "Facebook Reels"
    "MOJ" -> "Moj"
    "X" -> "X"
    "LINKEDIN" -> "LinkedIn"
    else -> platform ?: "Shorts"
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

/** Filled primary CTA — green when ready, grey when disabled. Deliberately a
 * single activation color (never blue); the dialog's Activate action uses the
 * same green. */
@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalScColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) ActivateGreen else colors.CardHover)
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