package com.shortscap.app.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.shortscap.app.activity.AppUsageColorProvider
import com.shortscap.app.charts.ChartSlice
import com.shortscap.app.charts.ChartStyle
import com.shortscap.app.charts.ScDistributionChart
import com.shortscap.app.charts.ScDonutCenterTotal
import com.shortscap.app.charts.ScPointTooltipCard
import com.shortscap.app.charts.ScSeriesChart
import com.shortscap.app.charts.ScTimeLegend
import com.shortscap.app.components.ScAppUsageIcon
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

/**
 * Brand color for a distribution slice — delegated to the centralized
 * [com.shortscap.app.activity.AppUsageColorProvider] (keyed by package
 * identity, never display label) so Activity, its report screens and Home
 * share ONE source of truth. The "Other" slice (packageName == null) stays
 * neutral; known apps keep their brand color regardless of rank/order.
 */
fun ActivitySlice.pieColor(colors: ScColors): Color =
    AppUsageColorProvider.colorFor(packageName, colors)

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
 * tabs (kept exactly as they are).
 *   - Daily (Phase 1.5/1.6)   → APPLICATION-centric: the chart shows the
 *               reportable applications used today with real app icons,
 *               brand-coloured, size-proportional segments, total-only
 *               center, NO percentages and NO hour-of-day; tapping a segment
 *               or row selects/highlights that application and shows its
 *               total usage.
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
    // Bumped by the reporting poll when persisted data changes, so the
    // real-data report recomputes instead of staying cached.
    dataTick: Long = 0L,
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
    val report = remember(period, dataTick) { ActivityRepository.reportFor(period) }

    // Distribution → shared chart slices (same data, pie-palette colors).
    // Phase 1.6 — the small Most Used Apps donut is duration-proportional
    // (value = real minutes, exactly like the Daily app donut) so segment
    // sizes reflect actual usage, not shares that would distort the ring.
    val chartSlices = remember(report, strings, colors) {
        report.distribution.map { slice ->
            ChartSlice(
                label = slice.displayName(strings),
                value = slice.minutes.toFloat(),
                color = slice.pieColor(colors),
            )
        }
    }
    // Phase 1.5 — the DAILY app-centric donut: segments are APPLICATIONS,
    // not hours. It uses the SAME reportable dataset as Most Used Apps
    // (report.distribution — per-package, consolidated across the day,
    // system/IME/launcher/ShortsCap already filtered). Value = real minutes
    // so the legend durations are exact. Kept index-aligned with
    // report.distribution (no filtering) so selection indices match the
    // slices everywhere. Weekly/Monthly keep their time-series segments.
    val appDonutSlices = remember(report, strings, colors) {
        report.distribution.map { slice ->
            ChartSlice(
                label = slice.displayName(strings),
                value = slice.minutes.toFloat(),
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
    val monthlyRanges = remember(period, dataTick) {
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

    // Weekly/Monthly time-series selection (day / date-range tooltips) —
    // DAILY now selects an APPLICATION instead (Phase 1.5, below). Selection
    // is tracked by LABEL so bar, line and donut taps resolve to the same
    // point even though the donut only draws the non-zero slices.
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
    // Phase 1.5 — DAILY application selection state. Tapping a donut segment
    // or a legend row selects that APPLICATION (by package identity — "Other"
    // has no package, so tapping it deselects). The selected segment, the
    // legend row and the matching Most Used Apps row all highlight together.
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }
    val selectedAppIndex: Int? = report.distribution
        .indexOfFirst { it.packageName != null && it.packageName == selectedAppPackage }
        .takeIf { it >= 0 }
    val selectedAppSlice = selectedAppIndex?.let { report.distribution.getOrNull(it) }
    val onAppSliceTap: ((Int) -> Unit)? = { index ->
        val pkg = report.distribution.getOrNull(index)?.packageName
        selectedAppPackage = if (selectedAppPackage == pkg) null else pkg
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
            if (period == ActivityPeriod.DAILY) {
                // Phase 1.5 — DAILY is APPLICATION-centric. The card answers
                // "which applications did I use today, and for how long?" —
                // the chart shows the reportable apps (same dataset as Most
                // Used Apps), never hour-of-day segments. Tapping a segment
                // or legend row selects that application: it highlights
                // everywhere and a detail card shows its name + total usage
                // (no date, no clock time, no session timestamps).
                if (chartStyle == ChartStyle.BAR) {
                    ScDistributionChart(
                        slices = appDonutSlices,
                        chartStyle = ChartStyle.BAR,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        selectedIndex = selectedAppIndex,
                        onSliceClick = onAppSliceTap,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ScDistributionChart(
                            slices = appDonutSlices,
                            chartStyle = ChartStyle.CIRCULAR,
                            modifier = Modifier.size(190.dp),
                            centerContent = {
                                // Phase 1.3/1.5: the donut center shows ONLY
                                // the total (sum of reportable app usage) —
                                // the date caption stays above the chart.
                                ScDonutCenterTotal(total = totalText)
                            },
                            selectedIndex = selectedAppIndex,
                            onSliceClick = onAppSliceTap,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                // Phase 1.6 — the Daily application list shows REAL app icons
                // (one per slice), app name + total duration, and NO
                // percentages. Icon identity = package; colour = the SAME
                // AppUsageColorProvider colour as the donut segment.
                ScTimeLegend(
                    slices = appDonutSlices,
                    valueFormatter = valueFormatter,
                    onSliceClick = onAppSliceTap,
                    selectedIndex = selectedAppIndex,
                    showPercent = false,
                    icon = { index ->
                        ScAppUsageIcon(
                            packageName = report.distribution.getOrNull(index)?.packageName,
                            name = appDonutSlices.getOrNull(index)?.label.orEmpty(),
                            size = 20.dp,
                            corner = 6.dp,
                        )
                    },
                )
                // Selected application detail — real icon + name + total usage
                // only (no date, no clock time, no session timestamps).
                selectedAppSlice?.let { slice ->
                    Spacer(modifier = Modifier.height(12.dp))
                    ScPointTooltipCard(
                        title = slice.displayName(strings),
                        usage = formatWebDuration(slice.minutes, strings),
                        timeRange = null,
                        titleIcon = {
                            ScAppUsageIcon(
                                packageName = slice.packageName,
                                name = slice.displayName(strings),
                                size = 20.dp,
                                corner = 6.dp,
                            )
                        },
                        onClose = { selectedAppPackage = null },
                    )
                }
            } else {
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
                                    // Phase 1.3: the donut center shows ONLY the
                                    // total — the date caption stays above the
                                    // chart in the Usage Timeline card header.
                                    ScDonutCenterTotal(total = totalText)
                                },
                                selectedIndex = donutSelectedIndex,
                                onSliceClick = onDonutTap,
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        ScTimeLegend(
                            slices = timeSlices,
                            valueFormatter = valueFormatter,
                            onSliceClick = onDonutTap,
                            selectedIndex = donutSelectedIndex,
                        )
                    }
                }
                // Tapped point detail — exact date/usage for the selected
                // day or date range (Weekly/Monthly never carried hour data).
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
        }

        // Most used apps — the SAME reportable dataset as the Daily app donut
        // (report.distribution), rendered in the chart style. Tapping the
        // mini donut or a row selects that application and highlights it here
        // and in the Daily chart above (Phase 1.5).
        ScCard(modifier = Modifier.fillMaxWidth()) {
            Text(strings.activityMostUsedApps, color = colors.TextSecondary, style = ScTextStyles.SectionTitle, modifier = Modifier.padding(bottom = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ScDistributionChart(
                    slices = chartSlices,
                    chartStyle = chartStyle,
                    modifier = Modifier.size(100.dp),
                    selectedIndex = selectedAppIndex,
                    onSliceClick = onAppSliceTap,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    report.distribution.forEach { slice ->
                        val isSelected = slice.packageName != null && slice.packageName == selectedAppPackage
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (isSelected) Modifier.background(colors.Accent.copy(alpha = 0.10f)) else Modifier,
                                )
                                .then(
                                    if (isSelected) Modifier.padding(horizontal = 6.dp, vertical = 4.dp) else Modifier,
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        selectedAppPackage =
                                            if (selectedAppPackage == slice.packageName) null else slice.packageName
                                    },
                                ),
                        ) {
                            // Phase 1.6 — real launcher icon (brand-letter tile
                            // fallback), sharing the same colour identity as the
                            // Daily donut segment and main legend.
                            ScAppUsageIcon(
                                packageName = slice.packageName,
                                name = slice.displayName(strings),
                                size = 18.dp,
                                corner = 5.dp,
                            )
                            Text(
                                slice.displayName(strings),
                                color = if (isSelected) colors.TextPrimary else colors.TextSecondary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.5.sp,
                                modifier = Modifier.weight(1f),
                            )
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

        // Stats row — real period values from the persisted app-usage
        // sessions (closed foreground sessions + their average length).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScCard(modifier = Modifier.weight(1f)) {
                Text("${report.unlockCount}", color = colors.TextPrimary, style = ScTextStyles.StatValue)
                Text(strings.activityUnlockCount, color = colors.TextSecondary, style = ScTextStyles.Label)
            }
            ScCard(modifier = Modifier.weight(1f)) {
                Text(formatClockSeconds(report.avgSessionSeconds), color = colors.TextPrimary, style = ScTextStyles.StatValue)
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

/** "6m 40s" / "1h 5m" — clock-style duration text for the avg-session stat. */
private fun formatClockSeconds(totalSeconds: Long): String = when {
    totalSeconds <= 0L -> "0s"
    totalSeconds < 60L -> "${totalSeconds}s"
    totalSeconds < 3_600L -> "${totalSeconds / 60}m ${totalSeconds % 60}s"
    else -> "${totalSeconds / 3_600}h ${(totalSeconds % 3_600) / 60}m"
}
