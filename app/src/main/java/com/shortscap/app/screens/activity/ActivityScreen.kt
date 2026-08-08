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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
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
import com.shortscap.app.activity.ActivitySlice
import com.shortscap.app.charts.ChartSlice
import com.shortscap.app.charts.ChartStyle
import com.shortscap.app.charts.ScDistributionChart
import com.shortscap.app.charts.ScDonutCenterTotal
import com.shortscap.app.charts.ScPointTooltipCard
import com.shortscap.app.charts.ScSeriesChart
import com.shortscap.app.charts.ScTimeLegend
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScChip
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.screens.web.formatWebDuration
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScColors
import com.shortscap.app.theme.ScTextStyles
import kotlin.math.roundToInt

/** Localized display name — the \u201cOther\u201d slice reads from the active catalog. */
fun ActivitySlice.displayName(strings: AppStrings): String =
    if (id == "other") strings.activityOther else name

/** Theme-aware app palette color for a distribution slice. */
fun ActivitySlice.pieColor(colors: ScColors): Color = when (id) {
    "instagram" -> colors.PieInstagram
    "youtube" -> colors.PieYouTube
    "chrome" -> colors.PieChrome
    else -> colors.PieOther
}

/** Cycling time-slice palette so every hour / day / range has a distinct hue. */
fun timeSliceColor(colors: ScColors, index: Int): Color {
    val palette = listOf(
        colors.Accent,
        colors.Accent2,
        colors.Success,
        colors.Warning,
        colors.Danger,
        colors.PieInstagram,
        colors.PieYouTube,
        colors.PieChrome,
    )
    return palette[index % palette.size]
}

/**
 * Activity — daily / weekly / monthly usage with the Daily | Weekly | Monthly
 * tabs (kept exactly as they are). TIME + DAY + DATE are the primary
 * information in EVERY chart style:
 *   - Daily   → the complete 24-hour timeline with readable 3-hour markers
 *               (12 AM, 3 AM, … 9 PM); tapping any bar/point shows the exact
 *               clock window and duration.
 *   - Weekly  → Monday–Sunday, every bar carrying its day + real date
 *               ("Mon" / "Aug 4") and duration; tapping shows the full day.
 *   - Monthly → the current month split into 7-day date ranges (Aug 1–7,
 *               Aug 8–14, …); tapping a range shows its total and opens the
 *               per-day detail.
 * Each period aggregates the SAME raw records via [ActivityRepository] and
 * the globally selected chart style only changes how the identical data is
 * drawn — the values are never altered.
 */
@Composable
fun ActivityScreen(
    range: String,
    onRangeChange: (String) -> Unit,
    chartStyle: ChartStyle,
    onOpenReport: (ActivityPeriod) -> Unit,
    onOpenRange: (ActivityRange) -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    val period = remember(range) {
        when (range) {
            "Weekly" -> ActivityPeriod.WEEKLY
            "Monthly" -> ActivityPeriod.MONTHLY
            else -> ActivityPeriod.DAILY
        }
    }
    val report = remember(period) { ActivityRepository.reportFor(period) }

    // Distribution → shared chart slices (same data, pie-palette colors).
    val chartSlices = remember(report, strings, colors) {
        report.distribution.map { slice ->
            ChartSlice(
                label = slice.displayName(strings),
                value = slice.percent.toFloat(),
                color = slice.pieColor(colors),
            )
        }
    }
    // Period time series → the exact same data used by every visualization.
    val seriesSlices = remember(report, colors) {
        report.points.mapIndexed { index, point ->
            ChartSlice(
                label = point.label,
                value = point.minutes.toFloat(),
                color = if (chartStyle == ChartStyle.CIRCULAR) timeSliceColor(colors, index) else colors.Accent,
            )
        }
    }
    // Time-distribution slices for the donut (zero hours are omitted so the
    // ring and its legend stay clean).
    val timeSlices = remember(report, colors) {
        report.points.mapIndexed { index, point ->
            if (point.minutes <= 0) null
            else ChartSlice(point.label, point.minutes.toFloat(), timeSliceColor(colors, index))
        }.filterNotNull()
    }
    // Monthly ranges (labels match report.points) for the tooltip drill-down.
    val monthlyRanges = remember(period) {
        if (period == ActivityPeriod.MONTHLY) ActivityRepository.monthlyRanges() else emptyList()
    }

    val totalText = formatWebDuration(report.totalMinutes, strings)
    // Daily = full 24-hour data with sparse, readable 3-hour markers so the
    // timeline never looks crowded; weekly = day+date labels on two lines;
    // monthly = one line per date range. Every point keeps its own duration.
    val labelEvery = if (period == ActivityPeriod.DAILY) 3 else 1
    val labelLines = if (period == ActivityPeriod.WEEKLY) 2 else 1
    val valueEvery = 1
    // Duration labels above the bars/points are only shown for Weekly/Monthly;
    // the Daily chart stays clean (its exact values appear in the tooltip).
    val showValues = period != ActivityPeriod.DAILY
    val valueFontSp = 8.5f

    // Tap-to-select — the tooltip shows the point's exact date/time/usage in
    // every chart style. Selection is tracked by LABEL so bar, line and donut
    // taps always resolve to the same point even though the donut only draws
    // the non-zero slices.
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
    // Slide-to-inspect (Graph chart only): unlike taps, dragging ALWAYS selects
    // the nearest point — re-pressing a selected point must not clear it.
    val onPointDrag: ((Int) -> Unit)? = { index ->
        report.points.getOrNull(index)?.let { selectedLabel = it.label }
    }
    val onDonutTap: ((Int) -> Unit)? = { index ->
        timeSlices.getOrNull(index)?.let { toggleSelect(it.label) }
    }
    // Monthly: the tooltip's "View details" action opens the per-day detail
    // for the tapped date range.
    val tooltipAction: (() -> Unit)? = if (period == ActivityPeriod.MONTHLY) {
        selectedPoint?.let { point ->
            monthlyRanges.firstOrNull { it.label == point.label }?.let { range ->
                { onOpenRange(range) }
            }
        }
    } else null
    val tooltipActionLabel = if (period == ActivityPeriod.MONTHLY) strings.chartViewDetails else null
    val valueFormatter: (Float) -> String = { minutes ->
        formatWebDuration(minutes.roundToInt(), strings)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(strings.activityTitle, color = colors.TextPrimary, style = ScTextStyles.H1)

        val rangeOptions = listOf(strings.activityDaily to "Daily", strings.activityWeekly to "Weekly", strings.activityMonthly to "Monthly")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rangeOptions.forEach { (label, key) ->
                ScChip(label = label, active = range == key, onClick = { onRangeChange(key) })
            }
        }

        // Usage timeline — the period's time data, drawn in the global chart
        // style. BAR/GRAPH: thin bars / line with durations; CIRCULAR: the
        // SAME time slices as a donut with a label+duration legend.
        ScCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(strings.activityUsageTimeline, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
                Text(range, color = colors.TextSecondary, fontSize = 12.sp)
            }
            Text(
                totalText,
                color = colors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            // The exact calendar span the selected period covers — TIME and
            // DATE stay visible right next to the chart in every style.
            Text(
                ActivityRepository.periodDateCaption(period),
                color = colors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            when (chartStyle) {
                ChartStyle.BAR, ChartStyle.GRAPH -> ScSeriesChart(
                    points = seriesSlices,
                    chartStyle = chartStyle,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    showValues = showValues,
                    valueFormatter = valueFormatter,
                    labelEvery = labelEvery,
                    valueEvery = valueEvery,
                    labelLines = labelLines,
                    valueFontSp = valueFontSp,
                    onPointTap = onPointTap,
                    onPointDrag = onPointDrag,
                    selectedIndex = barSelectedIndex,
                )
                ChartStyle.CIRCULAR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScDistributionChart(
                            slices = timeSlices,
                            chartStyle = ChartStyle.CIRCULAR,
                            modifier = Modifier.size(190.dp),
                            centerContent = {
                                ScDonutCenterTotal(
                                    total = totalText,
                                    subtitle = ActivityRepository.periodDateCaption(period),
                                )
                            },
                            selectedIndex = donutSelectedIndex,
                            onSliceClick = onDonutTap,
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    // The Daily circle chart's hourly timeline stays compact by
                    // default (first 4 entries + Show More) so the screen never
                    // becomes one long list; Weekly/Monthly legends keep every
                    // row visible exactly as before.
                    ScTimeLegend(
                        slices = timeSlices,
                        valueFormatter = valueFormatter,
                        onSliceClick = onDonutTap,
                        maxVisible = if (period == ActivityPeriod.DAILY) 4 else null,
                    )
                }
            }
            // Tapped point detail — exact date/time/usage, in every style.
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

        // Most used apps — the same distribution, rendered in the chart style.
        ScCard(modifier = Modifier.fillMaxWidth()) {
            Text(strings.activityMostUsedApps, color = colors.TextSecondary, style = ScTextStyles.SectionTitle, modifier = Modifier.padding(bottom = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ScDistributionChart(slices = chartSlices, chartStyle = chartStyle, modifier = Modifier.size(100.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    report.distribution.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(slice.pieColor(colors)))
                            Text(slice.displayName(strings), color = colors.TextSecondary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                            // Real usage duration ("4h 35m"), derived from the
                            // period's aggregated data — never a percentage.
                            Text(
                                formatWebDuration(slice.minutes, strings),
                                color = colors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                            )
                        }
                    }
                }
            }
        }

        // Stats row — unchanged demo values.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScCard(modifier = Modifier.weight(1f)) {
                Text("38", color = colors.TextPrimary, style = ScTextStyles.StatValue)
                Text(strings.activityUnlockCount, color = colors.TextSecondary, style = ScTextStyles.Label)
            }
            ScCard(modifier = Modifier.weight(1f)) {
                Text("6m 40s", color = colors.TextPrimary, style = ScTextStyles.StatValue)
                Text(strings.activityAvgSession, color = colors.TextSecondary, style = ScTextStyles.Label)
            }
        }

        // Reports — each row opens its own dedicated report screen.
        Column {
            Text(strings.activityReports, color = colors.TextSecondary, style = ScTextStyles.SectionTitle, modifier = Modifier.padding(bottom = 12.dp))
            val reportOptions = listOf(
                strings.activityWeeklyReport to ActivityPeriod.WEEKLY,
                strings.activityMonthlyReport to ActivityPeriod.MONTHLY,
            )
            reportOptions.forEach { (label, reportPeriod) ->
                ScCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    onClick = { onOpenReport(reportPeriod) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(17.dp))
                            Text(label, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
