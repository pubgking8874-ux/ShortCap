package com.shortscap.app.screens.web

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScChip
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScEntityRow
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.model.ScEntity
import com.shortscap.app.model.ScEntityType
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.web.WebAnalyticsPeriod
import com.shortscap.app.web.WebAnalyticsSummary

/**
 * Web Usage Analytics — a dedicated secondary page opened ONLY from the
 * Web Time card on the main Web screen. It never replaces the Website
 * Blocking screen.
 *
 * Exclusively website usage (no app/Android activity): a large proportional
 * donut chart with the total in the center, a website-wise breakdown, and
 * Today / Week / Month reports. Today shows today's distribution; Week and
 * Month add a graphical daily/weekly trend chart. Data arrives already
 * aggregated through WebRepository (pure function) — never hardcoded.
 */
@Composable
fun WebAnalyticsScreen(
    period: WebAnalyticsPeriod,
    onPeriodChange: (WebAnalyticsPeriod) -> Unit,
    summary: WebAnalyticsSummary,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize()) {
        WebSubScreenTopBar(title = strings.webAnalyticsTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Period headline + total (Today's / Weekly / Monthly Web Usage)
            val headline = when (period) {
                WebAnalyticsPeriod.TODAY -> strings.webTodayUsageTitle
                WebAnalyticsPeriod.WEEK -> strings.webWeeklyUsageTitle
                WebAnalyticsPeriod.MONTH -> strings.webMonthlyUsageTitle
            }
            Text(headline, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
            Text(
                formatWebDuration(summary.totalMinutes, strings),
                color = colors.TextPrimary,
                style = ScTextStyles.BigStat,
            )

            // Today / Week / Month report selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScChip(label = strings.webPeriodToday, active = period == WebAnalyticsPeriod.TODAY, onClick = { onPeriodChange(WebAnalyticsPeriod.TODAY) })
                ScChip(label = strings.webPeriodWeek, active = period == WebAnalyticsPeriod.WEEK, onClick = { onPeriodChange(WebAnalyticsPeriod.WEEK) })
                ScChip(label = strings.webPeriodMonth, active = period == WebAnalyticsPeriod.MONTH, onClick = { onPeriodChange(WebAnalyticsPeriod.MONTH) })
            }

            // Donut chart — each website a proportional segment, total in center.
            ScCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.size(216.dp), contentAlignment = Alignment.Center) {
                        WebDonutChart(summary = summary)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                formatWebDuration(summary.totalMinutes, strings),
                                color = colors.TextPrimary,
                                style = ScTextStyles.BigStat,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                periodLabel(period, strings),
                                color = colors.TextSecondary,
                                style = ScTextStyles.Label,
                            )
                        }
                    }
                }
            }

            // Weekly / monthly graphical report (not shown for Today — today's
            // distribution is fully covered by the donut + breakdown).
            if (period != WebAnalyticsPeriod.TODAY) {
                ScCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(strings.webTrendTitle, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
                        WebTrendChart(summary = summary)
                    }
                }
            }

            // Website-wise breakdown.
            ScCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(strings.webBreakdownTitle, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
                    if (summary.items.isEmpty()) {
                        ScEmptyState(
                            iconKey = IconKey.WEB_ANALYTICS,
                            title = strings.webNoDataTitle,
                            subtitle = strings.webNoDataDesc,
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        summary.items.forEachIndexed { index, item ->
                            ScEntityRow(
                                entity = ScEntity(
                                    id = item.domain,
                                    title = item.displayName,
                                    type = ScEntityType.WEBSITE,
                                    websiteUrl = item.domain,
                                    fallbackColor = colors.TextSecondary,
                                ),
                                subtitle = item.domain,
                                usageInfo = formatWebDuration(item.durationMinutes, strings),
                                restrictionStatus = "${item.percentage}%",
                            )
                            if (index < summary.items.size - 1) ScDivider(modifier = Modifier.padding(start = 62.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun periodLabel(period: WebAnalyticsPeriod, strings: AppStrings): String =
    when (period) {
        WebAnalyticsPeriod.TODAY -> strings.webPeriodToday
        WebAnalyticsPeriod.WEEK -> strings.webPeriodWeek
        WebAnalyticsPeriod.MONTH -> strings.webPeriodMonth
    }

/**
 * Segmented donut chart — one arc per website, sweep proportional to its
 * share of the total usage. Animated sweep on first composition / period
 * change.
 */
@Composable
private fun WebDonutChart(summary: WebAnalyticsSummary) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(summary.period, summary.totalMinutes) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing),
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (summary.totalMinutes <= 0 || summary.items.isEmpty()) return@Canvas
        val stroke = 26.dp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        var start = -90f
        summary.items.forEachIndexed { index, item ->
            val fraction = item.durationMinutes / summary.totalMinutes.toFloat()
            val sweep = 360f * fraction * progress.value
            drawArc(
                color = DonutColors[index % DonutColors.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            start += 360f * fraction
        }
    }
}

/** Simple rounded bar chart for the weekly / monthly trend data. */
@Composable
private fun WebTrendChart(summary: WebAnalyticsSummary) {
    val colors = LocalScColors.current
    if (summary.trend.isEmpty()) return
    val points = summary.trend

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            val max = points.maxOf { it.durationMinutes }.coerceAtLeast(1)
            val slot = size.width / points.size
            val barWidth = slot * 0.44f
            val brush = Brush.verticalGradient(
                listOf(colors.Accent, colors.Accent.copy(alpha = 0.45f)),
            )
            points.forEachIndexed { index, point ->
                val barHeight = (size.height * 0.86f * (point.durationMinutes / max.toFloat()))
                    .coerceAtLeast(3.dp.toPx())
                val x = slot * index + (slot - barWidth) / 2f
                val y = size.height - barHeight
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    point.label,
                    color = colors.TextSecondary,
                    style = ScTextStyles.Caption.copy(fontSize = 10.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Tasteful category shades shared with the app's color language — the color
 *  belongs to the data slice, and stays readable on dark and light. */
private val DonutColors = listOf(
    Color(0xFF3B82F6),
    Color(0xFF06B6D4),
    Color(0xFF22C55E),
    Color(0xFFF59E0B),
    Color(0xFFF97316),
    Color(0xFFEC4899),
    Color(0xFF8B5CF6),
    Color(0xFF14B8A6),
)
