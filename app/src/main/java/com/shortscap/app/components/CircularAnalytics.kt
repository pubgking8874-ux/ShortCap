package com.shortscap.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.ScCircularMetric
import com.shortscap.app.study.StudyAnimationType
import com.shortscap.app.study.formatStudyCountdown
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Mirrors the Home hero "circular analytics" widget: a large animated
 * progress ring with the metric value in the center and the metric label
 * below. Fully reusable — any [ScCircularMetric] renders without new UI
 * (Shorts watch time, shorts watched count, weekly/monthly usage, goal
 * progress, ...). Ring colors come from the active Material theme so they
 * adapt automatically to dark/light mode.
 */
@Composable
fun ScCircularMetricRing(
    metric: ScCircularMetric,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val ringBrush = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )

    val progress = remember { Animatable(0f) }
    LaunchedEffect(metric.progress) {
        progress.animateTo(
            targetValue = metric.progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)

                // Animated progress arc (no track — only the ring itself is drawn)
                if (progress.value > 0f) {
                    drawArc(
                        brush = ringBrush,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(metric.value, color = colors.TextPrimary, style = ScTextStyles.BigStat)
                if (metric.unit.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(metric.unit, color = colors.TextSecondary, style = ScTextStyles.BodySemiBold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            metric.label,
            color = colors.TextSecondary,
            style = ScTextStyles.SectionTitle,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Home hero widget: horizontally swipeable pages of [ScCircularMetricRing]
 * (one metric per page, smooth pager transition) with animated page-indicator
 * dots below. Passing more metrics to the list adds pages with no new UI.
 *
 * Study Mode (priority state): while a Study Mode session is active
 * ([studyModeActive]), a Study Mode page — "Study Mode Active" + the exact
 * timestamp-based remaining countdown + the reusable Watch/Timer animation —
 * is injected as the FIRST page and is fully TAPPABLE ([onStopStudyMode]):
 * tapping it opens the "Stop Study Mode?" confirmation which leads to the
 * shared Exit Passcode verification (Home Page exit path). The existing
 * metric pages (Watch Time, Shorts Count) move behind it but remain reachable
 * by horizontal swiping; the Shorts monitoring system itself is never touched
 * and returns to the front automatically when the session ends.
 *
 * Monitoring Paused (priority/exception state): when [monitoringPaused] is
 * true, a Monitoring Paused page is injected after the Study Mode page (or
 * as the first page when no session is active) — the existing metric pages
 * move behind it but remain reachable by horizontal swiping. Both injected
 * pages appear/disappear automatically from derived AppUiState flags (real
 * permission + session state), so the existing metric pages and their design
 * are never touched.
 */
@Composable
fun ScCircularAnalyticsCarousel(
    metrics: List<ScCircularMetric>,
    modifier: Modifier = Modifier,
    monitoringPaused: Boolean = false,
    onResumeMonitoring: (() -> Unit)? = null,
    studyModeActive: Boolean = false,
    studyRemainingMillis: Long = 0L,
    studyTotalMillis: Long = 0L,
    // Tapping the active Study Mode page → "Stop Study Mode?" → the shared
    // Exit Passcode verification (same flow as General → Study Mode).
    onStopStudyMode: (() -> Unit)? = null,
) {
    if (metrics.isEmpty()) return
    val colors = LocalScColors.current
    val totalPages = metrics.size + (if (studyModeActive) 1 else 0) + (if (monitoringPaused) 1 else 0)
    val pagerState = rememberPagerState(pageCount = { totalPages })

    // When Study Mode activates (or monitoring becomes paused), its priority
    // page appears at index 0 — snap the pager to it so the user immediately
    // sees the new state instead of whatever metric page they were on.
    // Fires only on an actual state CHANGE, so it never yanks the user back
    // while they are deliberately swiping through the metric pages.
    LaunchedEffect(studyModeActive, monitoringPaused) {
        if (studyModeActive || monitoringPaused) pagerState.scrollToPage(0)
    }

    // Study page leads (index 0), Monitoring Paused follows it, then the
    // existing metric pages — so the explicit Study Mode state is always the
    // first thing the user sees while it is active.
    fun isStudyPage(page: Int) = studyModeActive && page == 0
    fun isPausedPage(page: Int) = monitoringPaused && page == (if (studyModeActive) 1 else 0)
    fun metricFor(page: Int): ScCircularMetric =
        metrics[page - (if (studyModeActive) 1 else 0) - (if (monitoringPaused) 1 else 0)]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.Divider, RoundedCornerShape(22.dp))
            .padding(vertical = 24.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page ->
                when {
                    isStudyPage(page) -> "study-mode-active"
                    isPausedPage(page) -> "monitoring-paused"
                    else -> metricFor(page).id
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            // Every page fills the card width and centers the widget, so the
            // ring, value, and subtitle sit in the exact middle on all pages.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isStudyPage(page) -> ScStudyModePage(
                        remainingMillis = studyRemainingMillis,
                        totalMillis = studyTotalMillis,
                        onStop = onStopStudyMode,
                    )
                    isPausedPage(page) -> ScMonitoringPausedPage(onResumeMonitoring = onResumeMonitoring)
                    else -> ScCircularMetricRing(metric = metricFor(page))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until totalPages).forEach { index ->
                val selected = pagerState.currentPage == index
                val dotWidth by animateDpAsState(
                    targetValue = if (selected) 22.dp else 7.dp,
                    label = "pageDot$index",
                )
                Box(
                    modifier = Modifier
                        .width(dotWidth)
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(999.dp),
                        ),
                )
            }
        }
    }
}

/**
 * The Study Mode page injected as the first carousel page while a session is
 * active: a countdown ring (progress toward 00:00) with the exact remaining
 * time in the center, the reusable Watch/Timer Study animation, and the
 * "Study Mode Active" label. The ENTIRE page is tappable ([onStop]): tapping
 * opens the "Stop Study Mode?" confirmation which leads to the SHARED Focus
 * Exit Passcode verification — it never stops Study Mode directly. The normal
 * Shorts monitoring pages stay untouched and reachable by swiping.
 */
@Composable
private fun ScStudyModePage(
    remainingMillis: Long,
    totalMillis: Long,
    onStop: (() -> Unit)? = null,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val ringBrush = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )
    // Progress toward the end: 0 at start → 1 at 00:00.
    val progress = if (totalMillis > 0) {
        (1f - remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    } else 0f

    // Subtle press feedback confirms the whole page is the interactive
    // control; the tap only opens the "Stop Study Mode?" confirmation.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressBg by animateColorAsState(
        targetValue = if (pressed) colors.CardHover else Color.Transparent,
        animationSpec = tween(120),
        label = "studyPagePress",
    )
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier
            .clip(shape)
            .background(pressBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = onStop != null,
                onClick = { onStop?.invoke() },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                // Subtle full track so progress toward 00:00 is always visible.
                drawArc(
                    color = colors.Divider,
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
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                formatStudyCountdown(remainingMillis),
                color = colors.TextPrimary,
                style = ScTextStyles.BigStat,
            )
        }

        Spacer(Modifier.height(10.dp))
        // Reusable Study Mode animation — Watch/Timer today; future styles
        // (Study/Book, Focus/Cartoon) swap in via StudyAnimationType later.
        ScStudyAnimation(type = StudyAnimationType.WATCH)

        Spacer(Modifier.height(10.dp))
        Text(
            strings.studyHomeTitle,
            color = colors.TextPrimary,
            style = ScTextStyles.SectionTitle,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Reusable Study Mode animation — the Home page visualization shown while a
 * session is active. Dispatches on [StudyAnimationType] so a future
 * Appearance → Study Animation setting can switch styles WITHOUT rebuilding
 * the Home page logic. Today only [StudyAnimationType.WATCH] is animated
 * (a clean watch/timer: sweeping hand + tick marks + lock badge conveying
 * "time is running / Study Mode is active / protected"); the other values
 * render simple static placeholders until their animations are added.
 */
@Composable
fun ScStudyAnimation(
    type: StudyAnimationType,
    modifier: Modifier = Modifier,
) {
    when (type) {
        StudyAnimationType.WATCH -> WatchTimerAnimation(modifier)
        // Future styles — static placeholders so the seam works end-to-end;
        // their animations plug in later without touching the Home page.
        StudyAnimationType.BOOK -> StaticStudyIcon(Icons.Filled.MenuBook, modifier)
        StudyAnimationType.FOCUS -> StaticStudyIcon(Icons.Filled.CenterFocusStrong, modifier)
    }
}

/** Clean watch/timer: face + ticks + endlessly sweeping hand + lock badge. */
@Composable
private fun WatchTimerAnimation(modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    val transition = rememberInfiniteTransition(label = "studyWatchTransition")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "studyWatchHand",
    )

    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        // Watch face + 12 tick marks.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val stroke = 2.dp.toPx()
            drawCircle(color = colors.Divider, radius = r - stroke / 2f, style = Stroke(stroke))
            val cx = size.width / 2f
            val cy = size.height / 2f
            repeat(12) { i ->
                val angle = Math.toRadians((i * 30 - 90).toDouble())
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                val major = i % 3 == 0
                val outer = r - 5.dp.toPx()
                val inner = if (major) r - 13.dp.toPx() else r - 10.dp.toPx()
                drawLine(
                    color = if (major) colors.TextSecondary else colors.Divider,
                    start = Offset(cx + cos * inner, cy + sin * inner),
                    end = Offset(cx + cos * outer, cy + sin * outer),
                    strokeWidth = if (major) 1.8.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        // Endlessly sweeping hand — "time is running".
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension / 2f
                drawLine(
                    color = colors.Accent,
                    start = Offset(cx, cy + 5.dp.toPx()),
                    end = Offset(cx, cy - (r - 8.dp.toPx())),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        // Center cap.
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.Accent),
        )
        // Lock badge — "the session is protected".
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.Card)
                .border(1.dp, colors.Divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(11.dp))
        }
    }
}

/** Static icon tile for the not-yet-animated future study styles. */
@Composable
private fun StaticStudyIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.CardHover),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(28.dp))
    }
}

/**
 * The Monitoring Paused page injected as the first carousel page while a
 * required monitoring permission is missing.
 *
 * Design: a soft, theme-consistent disc (Warning tint — the app's established
 * "paused/inactive" token, used by the Monitoring status too) with the pause
 * icon centered, and ONLY the "Monitoring Paused" label below — no description
 * and no separate button. The 190dp disc matches the metric ring footprint
 * exactly, so the card keeps its exact height (no empty space, no layout
 * shift). The ENTIRE circle is the interactive control: tapping it starts the
 * permission check + resume flow ([onResumeMonitoring]); a subtle press state
 * confirms it is tappable (null callback renders it non-interactive).
 */
@Composable
private fun ScMonitoringPausedPage(onResumeMonitoring: (() -> Unit)?) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val discColor by animateColorAsState(
        targetValue = if (pressed) colors.Warning.copy(alpha = 0.20f) else colors.Warning.copy(alpha = 0.10f),
        animationSpec = tween(120),
        label = "monitoringPausedDisc",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(discColor)
                .border(1.dp, colors.Warning.copy(alpha = 0.30f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = onResumeMonitoring != null,
                    onClick = { onResumeMonitoring?.invoke() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PauseCircle,
                contentDescription = strings.homeMonitoringPausedTitle,
                tint = colors.Warning,
                modifier = Modifier.size(64.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            strings.homeMonitoringPausedTitle,
            color = colors.TextPrimary,
            style = ScTextStyles.SectionTitle,
            textAlign = TextAlign.Center,
        )
    }
}
