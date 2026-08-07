package com.shortscap.app.screens.activity

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.activity.ActivityPeriod
import com.shortscap.app.activity.ActivityRange
import com.shortscap.app.activity.ActivityRepository
import com.shortscap.app.charts.ChartSlice
import com.shortscap.app.charts.ChartStyle
import com.shortscap.app.charts.ScDistributionChart
import com.shortscap.app.charts.ScDonutCenterTotal
import com.shortscap.app.charts.ScPointTooltipCard
import com.shortscap.app.charts.ScSeriesChart
import com.shortscap.app.charts.ScTimeLegend
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScDivider
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.screens.web.WebSubScreenTopBar
import com.shortscap.app.screens.web.formatWebDuration
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlin.math.roundToInt

/**
 * Dedicated Weekly / Monthly Activity Report screen — a full professional
 * summary of the period's usage, opened from the Activity page's Reports
 * section (never an inline expansion). When [range] is provided it becomes
 * the per-day detail for one monthly date range (opened from a monthly
 * range's tooltip).
 *
 * Everything comes from [ActivityRepository]: the chart renders the SAME
 * time data in the user's globally selected chart style
 * (Settings → Appearance → Chart), the date span is shown beside the chart,
 * and tapping any bar / point / slice surfaces the exact day + date + usage
 * in every style. The summary rows (total usage, busiest day, most-used app,
 * trend) plus the Shorts stats are derived from that same structured report —
 * nothing is hardcoded in the UI. A future backend fills the identical
 * report shape with no screen changes.
 */
@Composable
fun ActivityReportScreen(
    period: ActivityPeriod,
    chartStyle: ChartStyle,
    onBack: () -> Unit,
    range: ActivityRange? = null,
    onOpenRange: ((ActivityRange) -> Unit)? = null,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val report = remember(period, range) {
        if (range != null) ActivityRepository.rangeReportFor(range)
        else ActivityRepository.reportFor(period)
    }

    val seriesSlices = remember(report, colors, chartStyle) {
        report.points.mapIndexed { index, point ->
            ChartSlice(
                label = point.label,
                value = point.minutes.toFloat(),
                color = if (chartStyle == ChartStyle.CIRCULAR) timeSliceColor(colors, index) else colors.Accent,
            )
        }
    }
    val timeSlices = remember(report, colors) {
        report.points.mapIndexed { index, point ->
            if (point.minutes <= 0) null
            else ChartSlice(point.label, point.minutes.toFloat(), timeSliceColor(colors, index))
        }.filterNotNull()
    }
    // Monthly report ranges (labels match report.points) for the drill-down.
    val monthlyRanges = remember(period) {
        if (period == ActivityPeriod.MONTHLY && range == null) ActivityRepository.monthlyRanges() else emptyList()
    }

    val totalText = formatWebDuration(report.totalMinutes, strings)
    val usageTitle = when {
        range != null -> strings.activityDailyUsage
        period == ActivityPeriod.WEEKLY -> strings.activityWeeklyUsage
        period == ActivityPeriod.MONTHLY -> strings.activityMonthlyUsage
        else -> strings.activityDailyUsage
    }
    val title = when {
        range != null -> range.label
        period == ActivityPeriod.WEEKLY -> strings.activityWeeklyReport
        period == ActivityPeriod.MONTHLY -> strings.activityMonthlyReport
        else -> strings.activityReports
    }
    // Weekdays and range detail carry day+date labels ("Mon Aug 4") — two
    // lines keep them readable; monthly ranges use one line ("Aug 1–7").
    val labelEvery = 1
    val valueEvery = 1
    val labelLines = if (range != null || period == ActivityPeriod.WEEKLY) 2 else 1
    val valueFormatter: (Float) -> String = { minutes ->
        formatWebDuration(minutes.roundToInt(), strings)
    }

    // Tap-to-select — same exact date/time/usage tooltip in every style.
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    val selectedPoint = report.points.firstOrNull { it.label == selectedLabel }
    val barSelectedIndex = report.points.indexOfFirst { it.label == selectedLabel }.takeIf { it >= 0 }
    val donutSelectedIndex = timeSlices.indexOfFirst { it.label == selectedLabel }.takeIf { it >= 0 }
    fun toggleSelect(label: String) {
        selectedLabel = if (selectedLabel == label) null else label
    }
    val onPointTap: ((Int) -> Unit)? = { index ->
        report.points.getOrNull(index)?.let { toggleSelect(it.label) }
    }
    // Slide-to-inspect (Graph chart only): dragging always selects the nearest
    // point (never toggles) so the tooltip can't vanish mid-inspection.
    val onPointDrag: ((Int) -> Unit)? = { index ->
        report.points.getOrNull(index)?.let { selectedLabel = it.label }
    }
    val onDonutTap: ((Int) -> Unit)? = { index ->
        timeSlices.getOrNull(index)?.let { toggleSelect(it.label) }
    }
    // Monthly main report: the tooltip's "View details" action opens the
    // per-day detail for the tapped date range.
    val tooltipAction: (() -> Unit)? = if (period == ActivityPeriod.MONTHLY && range == null) {
        selectedPoint?.let { point ->
            monthlyRanges.firstOrNull { it.label == point.label }?.let { r ->
                { onOpenRange?.invoke(r) }
            }
        }
    } else null
    val tooltipActionLabel = if (period == ActivityPeriod.MONTHLY && range == null) strings.chartViewDetails else null

    val trendText = (if (report.trendPercent > 0) "+" else "") + "${report.trendPercent}%"
    val trendColor = if (report.trendPercent <= 0) colors.Success else colors.Danger
    val mostUsed = report.distribution.maxByOrNull { it.percent }

    Column(modifier = Modifier.fillMaxSize()) {
        WebSubScreenTopBar(title = title, onBack = onBack)

        // No inner scroll: the tab container (ScNavHost) already scrolls this
        // content, exactly like every other tab screen — a nested scrollable
        // would fight the outer one on overscroll.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Headline — total usage for the period / range, with the exact
            // calendar span beneath it so DATE stays visible in every style.
            Text(usageTitle, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
            Text(totalText, color = colors.TextPrimary, style = ScTextStyles.BigStat)
            if (range == null) {
                Text(
                    ActivityRepository.periodDateCaption(period),
                    color = colors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Period chart — the same time data, drawn in the globally selected
            // chart style (thin bars / line, or a donut of the time slices).
            ScCard(modifier = Modifier.fillMaxWidth()) {
                when (chartStyle) {
                    ChartStyle.BAR, ChartStyle.GRAPH -> ScSeriesChart(
                        points = seriesSlices,
                        chartStyle = chartStyle,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        showValues = true,
                        valueFormatter = valueFormatter,
                        labelEvery = labelEvery,
                        valueEvery = valueEvery,
                        labelLines = labelLines,
                        onPointTap = onPointTap,
                        onPointDrag = onPointDrag,
                        selectedIndex = barSelectedIndex,
                    )
                    ChartStyle.CIRCULAR -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ScDistributionChart(
                                slices = timeSlices,
                                chartStyle = ChartStyle.CIRCULAR,
                                modifier = Modifier.size(200.dp),
                                centerContent = {
                                    ScDonutCenterTotal(
                                        total = totalText,
                                        subtitle = usageTitle,
                                    )
                                },
                                selectedIndex = donutSelectedIndex,
                                onSliceClick = onDonutTap,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        ScTimeLegend(
                            slices = timeSlices,
                            valueFormatter = valueFormatter,
                            onSliceClick = onDonutTap,
                        )
                    }
                }
                // Tapped point detail — exact day/date + usage, every style.
                selectedPoint?.let { point ->
                    Spacer(modifier = Modifier.height(12.dp))
                    ScPointTooltipCard(
                        title = point.detailTitle ?: point.label,
                        usage = formatWebDuration(point.minutes, strings),
                        timeRange = point.timeRange,
                        actionLabel = tooltipActionLabel,
                        onAction = tooltipAction,
                        onClose = { selectedLabel = null },
                    )
                }
            }

            // Summary — the period at a glance, all derived from the report.
            ScCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.reportSummary, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
                    ReportRow(label = strings.reportTotalUsage, value = totalText)
                    ReportRow(label = strings.reportBusiestDay, value = report.busiestLabel)
                    mostUsed?.let { ReportRow(label = strings.reportMostUsedApp, value = it.displayName(strings)) }
                    ReportRow(label = strings.reportTrend, value = trendText, valueColor = trendColor)
                    ScDivider(modifier = Modifier.padding(vertical = 4.dp))
                    // Compact most-used-apps legend with the same distribution.
                    report.distribution.forEach { slice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(slice.pieColor(colors)))
                            Text(slice.displayName(strings), color = colors.TextSecondary, style = ScTextStyles.Body, modifier = Modifier.weight(1f))
                            Text("${slice.percent}%", color = colors.TextPrimary, fontWeight = FontWeight.SemiBold, style = ScTextStyles.Body)
                        }
                    }
                }
            }

            // Shorts — derived from the same report data (no separate system).
            ScCard(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(formatWebDuration(report.shortsMinutes, strings), color = colors.TextPrimary, style = ScTextStyles.StatValue)
                        Text(strings.reportShortsUsage, color = colors.TextSecondary, style = ScTextStyles.Label)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${report.shortsCount}", color = colors.TextPrimary, style = ScTextStyles.StatValue)
                        Text(strings.reportShortsWatched, color = colors.TextSecondary, style = ScTextStyles.Label)
                    }
                }
            }
        }
    }
}

/** Label / value row for the report summary (value optionally tinted). */
@Composable
private fun ReportRow(
    label: String,
    value: String,
    valueColor: Color = LocalScColors.current.TextPrimary,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = colors.TextSecondary, style = ScTextStyles.Body)
        Text(value, color = valueColor, style = ScTextStyles.BodySemiBold)
    }
}
