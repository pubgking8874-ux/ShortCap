package com.shortscap.app.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 */
@Composable
fun ScCircularAnalyticsCarousel(
    metrics: List<ScCircularMetric>,
    modifier: Modifier = Modifier,
) {
    if (metrics.isEmpty()) return
    val colors = LocalScColors.current
    val pagerState = rememberPagerState(pageCount = { metrics.size })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(listOf(colors.SummaryCardGradientStart, colors.SummaryCardGradientEnd)),
                RoundedCornerShape(22.dp),
            )
            .border(1.dp, colors.SummaryCardBorder, RoundedCornerShape(22.dp))
            .padding(vertical = 24.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            key = { metrics[it].id },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            ScCircularMetricRing(
                metric = metrics[page],
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            metrics.indices.forEach { index ->
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
