package com.shortscap.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.ScCircularMetric
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
 * Monitoring Paused (priority/exception state): when [monitoringPaused] is
 * true, a Monitoring Paused page is injected as the FIRST page — the existing
 * metric pages (Watch Time, Shorts Count) move behind it but remain reachable
 * by horizontal swiping. The page appears/disappears automatically from the
 * derived AppUiState.monitoringPaused flag (real permission state), so the
 * existing metric pages and their design are never touched.
 */
@Composable
fun ScCircularAnalyticsCarousel(
    metrics: List<ScCircularMetric>,
    modifier: Modifier = Modifier,
    monitoringPaused: Boolean = false,
    onResumeMonitoring: (() -> Unit)? = null,
) {
    if (metrics.isEmpty()) return
    val colors = LocalScColors.current
    val totalPages = metrics.size + if (monitoringPaused) 1 else 0
    val pagerState = rememberPagerState(pageCount = { totalPages })

    // Paused page sits at index 0; the metric pages shift right by one while
    // it is shown (Watch Time → page 1, Shorts Count → page 2).
    fun metricFor(page: Int): ScCircularMetric = metrics[if (monitoringPaused) page - 1 else page]

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
                if (monitoringPaused && page == 0) "monitoring-paused"
                else metricFor(page).id
            },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            // Every page fills the card width and centers the widget, so the
            // ring, value, and subtitle sit in the exact middle on all pages.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (monitoringPaused && page == 0) {
                    ScMonitoringPausedPage(onResumeMonitoring = onResumeMonitoring)
                } else {
                    ScCircularMetricRing(metric = metricFor(page))
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
