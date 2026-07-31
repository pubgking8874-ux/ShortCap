package com.shortscap.app.screens.activity

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScChip
import com.shortscap.app.model.AppUsageSlice
import com.shortscap.app.model.WeekData
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlin.math.min

private val reportSummary = "Screen time trended down 12% this period, with the biggest drop on weekday " +
    "evenings. Social apps still account for the largest share of usage."

/** Mirrors function ActivityScreen() { ... } */
@Composable
fun ActivityScreen(
    range: String,
    onRangeChange: (String) -> Unit,
    expandedReport: String?,
    onToggleReport: (String) -> Unit,
) {
    val colors = LocalScColors.current
    val slices = listOf(
        AppUsageSlice("Instagram", 42, colors.PieInstagram),
        AppUsageSlice("YouTube", 27, colors.PieYouTube),
        AppUsageSlice("Chrome", 18, colors.PieChrome),
        AppUsageSlice("Other", 13, colors.PieOther),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("Activity", color = colors.TextPrimary, style = ScTextStyles.H1)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Daily", "Weekly", "Monthly").forEach { r ->
                ScChip(label = r, active = range == r, onClick = { onRangeChange(r) })
            }
        }

        ScCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("Usage Timeline", color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
                Text(range, color = colors.TextSecondary, fontSize = 12.sp)
            }
            Text("21h 15m", color = colors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            UsageBarChart(modifier = Modifier.fillMaxWidth().height(150.dp))
        }

        ScCard(modifier = Modifier.fillMaxWidth()) {
            Text("Most Used Apps", color = colors.TextSecondary, style = ScTextStyles.SectionTitle, modifier = Modifier.padding(bottom = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                UsagePieChart(slices = slices, modifier = Modifier.size(100.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    slices.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(slice.color))
                            Text(slice.name, color = colors.TextSecondary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                            Text("${slice.value}%", color = colors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScCard(modifier = Modifier.weight(1f)) {
                Text("38", color = colors.TextPrimary, style = ScTextStyles.StatValue)
                Text("Unlock Count", color = colors.TextSecondary, style = ScTextStyles.Label)
            }
            ScCard(modifier = Modifier.weight(1f)) {
                Text("6m 40s", color = colors.TextPrimary, style = ScTextStyles.StatValue)
                Text("Avg. Session", color = colors.TextSecondary, style = ScTextStyles.Label)
            }
        }

        Column {
            Text("Reports", color = colors.TextSecondary, style = ScTextStyles.SectionTitle, modifier = Modifier.padding(bottom = 12.dp))
            listOf("Weekly Report", "Monthly Report").forEach { r ->
                val expanded = expandedReport == r
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
                ScCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    onClick = { onToggleReport(r) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(17.dp))
                            Text(r, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = colors.TextSecondary,
                            modifier = Modifier.size(16.dp).rotate(rotation),
                        )
                    }
                }
            }
            if (expandedReport != null) {
                ScCard(modifier = Modifier.fillMaxWidth()) {
                    Text(reportSummary, color = colors.TextSecondary, fontSize = 12.5.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

/** Mirrors the recharts <BarChart> for weekData */
@Composable
private fun UsageBarChart(modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    val maxVal = (WeekData.maxOfOrNull { it.minutes } ?: 1).toFloat()
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        WeekData.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier.width(20.dp).height(110.dp)) {
                    val barHeight = size.height * (day.minutes / maxVal)
                    drawRoundRect(
                        color = colors.Accent,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(day.day, color = colors.TextDisabled, fontSize = 11.sp)
            }
        }
    }
}

/** Mirrors the recharts <PieChart> for appUsage (donut, innerRadius 30 / outerRadius 48) */
@Composable
private fun UsagePieChart(slices: List<AppUsageSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }.toFloat()
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val diameter = min(size.width, size.height) - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = 360f * (slice.value / total)
            drawArc(
                color = slice.color,
                startAngle = startAngle + 3f,
                sweepAngle = sweep - 3f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}
