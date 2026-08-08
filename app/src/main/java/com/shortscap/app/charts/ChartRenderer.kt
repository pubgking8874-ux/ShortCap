package com.shortscap.app.charts

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Slate tone for the Activity Graph "monitoring" line — a slightly stronger,
 * darker gray than the accent so it reads clearly against both backgrounds.
 * Point markers stay accent blue; only the line itself uses this tone.
 */
private val MonitoringLineColor = Color(0xFF9AA3AF)

/**
 * ScDistributionChart — the reusable renderer for DISTRIBUTION data
 * (app shares, website shares). It takes the SAME [ChartSlice] data and
 * draws it as bars or a donut depending on the global [ChartStyle]:
 *  - [ChartStyle.BAR]      → thin proportional bars;
 *  - [ChartStyle.CIRCULAR] → circular/donut chart;
 *  - [ChartStyle.GRAPH]    → falls back to the donut (a line graph does not
 *                            represent a distribution share).
 *
 * The data (labels, values, colors) is never altered by the renderer —
 * switching the style only changes how the identical numbers are drawn.
 *
 * [centerContent] renders over the middle of the donut (e.g. total + period);
 * [showLabels] prints each slice's label under its bar, [labelEvery] shows
 * only every Nth label so long series stay readable. [selectedIndex]
 * highlights one slice and [onSliceClick] reports which slice was tapped —
 * both used by the screens to surface exact DATE/TIME/USAGE information.
 */
@Composable
fun ScDistributionChart(
    slices: List<ChartSlice>,
    chartStyle: ChartStyle,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false,
    labelEvery: Int = 1,
    centerContent: (@Composable () -> Unit)? = null,
    selectedIndex: Int? = null,
    onSliceClick: ((Int) -> Unit)? = null,
) {
    if (slices.isEmpty()) return
    when (chartStyle) {
        ChartStyle.BAR -> ScBarDistribution(
            slices = slices,
            modifier = modifier,
            showLabels = showLabels,
            labelEvery = labelEvery,
            selectedIndex = selectedIndex,
            onSliceClick = onSliceClick,
        )
        ChartStyle.CIRCULAR, ChartStyle.GRAPH -> ScDonutChart(
            slices = slices,
            modifier = modifier,
            centerContent = centerContent,
            selectedIndex = selectedIndex,
            onSliceClick = onSliceClick,
        )
    }
}

/**
 * ScSeriesChart — the reusable renderer for TIME-SERIES data (Activity
 * Daily / Weekly / Monthly charts and report charts). Same points, three
 * visualizations driven by the global [ChartStyle]:
 *  - [ChartStyle.BAR]   → thin compact bars with clean gaps for zero hours;
 *  - [ChartStyle.GRAPH] → a line/area graph with point markers;
 *  - [ChartStyle.CIRCULAR] → the same slices as a donut (callers may also
 *                            pass the slices straight to ScDistributionChart).
 *
 * [showValues] + [valueFormatter] print the actual usage duration above
 * each bar/point (omitted for zero values); [labelEvery] prints sparse
 * axis labels and [valueEvery] sparsifies the duration labels the same way
 * (dense series such as 24 hourly points stay readable on narrow screens).
 * [labelLines] renders each axis label on up to two lines ("Mon Aug 4" →
 * "Mon" / "Aug 4") so day+date labels stay legible slot-by-slot.
 * [valueFontSp] lets dense series use a smaller duration font.
 *
 * [onPointTap] reports the tapped point index and [selectedIndex] highlights
 * it — the screens use this to show a tooltip with the point's exact date,
 * clock time and duration in EVERY chart style. The values are never modified.
 *
 * [onPointDrag] (Graph chart only) is the slide-to-inspect interaction: as
 * the user drags a finger horizontally across the line, the NEAREST point is
 * reported continuously (no precise dot-tapping needed) and the chart draws
 * thin vertical + horizontal guide lines through it.
 */
@Composable
fun ScSeriesChart(
    points: List<ChartSlice>,
    chartStyle: ChartStyle,
    modifier: Modifier = Modifier,
    showValues: Boolean = false,
    valueFormatter: ((Float) -> String)? = null,
    labelEvery: Int = 1,
    valueEvery: Int = 1,
    labelLines: Int = 1,
    valueFontSp: Float = 8.5f,
    onPointTap: ((Int) -> Unit)? = null,
    onPointDrag: ((Int) -> Unit)? = null,
    selectedIndex: Int? = null,
    barAreaHeight: Dp = 150.dp,
) {
    if (points.isEmpty()) return
    when (chartStyle) {
        ChartStyle.BAR -> ScBarSeries(
            points = points,
            modifier = modifier,
            showValues = showValues,
            valueFormatter = valueFormatter,
            labelEvery = labelEvery,
            valueEvery = valueEvery,
            labelLines = labelLines,
            valueFontSp = valueFontSp,
            onPointTap = onPointTap,
            selectedIndex = selectedIndex,
            barAreaHeight = barAreaHeight,
        )
        ChartStyle.GRAPH -> ScLineSeries(
            points = points,
            modifier = modifier,
            showValues = showValues,
            valueFormatter = valueFormatter,
            labelEvery = labelEvery,
            valueEvery = valueEvery,
            labelLines = labelLines,
            valueFontSp = valueFontSp,
            onPointTap = onPointTap,
            onPointDrag = onPointDrag,
            selectedIndex = selectedIndex,
            barAreaHeight = barAreaHeight,
        )
        ChartStyle.CIRCULAR -> ScDonutChart(
            slices = points,
            modifier = modifier,
            selectedIndex = selectedIndex,
            onSliceClick = onPointTap,
        )
    }
}

/**
 * Segmented donut — one arc per slice, sweep proportional to its share of
 * the total. The center is left free for a [centerContent] overlay.
 *
 * Tapping inside the ring selects the slice under the finger ([onSliceClick]
 * with the slice index) and [selectedIndex] highlights it (wider arc, other
 * slices dimmed) — so circular charts surface the same point info as the
 * bar / line charts.
 */
@Composable
private fun ScDonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    centerContent: (@Composable () -> Unit)? = null,
    selectedIndex: Int? = null,
    onSliceClick: ((Int) -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
    }
    // Explicit Float accumulation — the donut's total never relies on
    // overload inference (sumOf selector typing), so slice fractions stay
    // unambiguous everywhere they are used.
    val total: Float = slices.fold(0f) { acc, slice -> acc + slice.value }
    val currentClick = rememberUpdatedState(onSliceClick)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slices, total) {
                    detectTapGestures { offset ->
                        val index = donutIndexAt(
                            x = offset.x,
                            y = offset.y,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            slices = slices,
                            total = total,
                        )
                        if (index != null) currentClick.value?.invoke(index)
                    }
                },
        ) {
            if (total <= 0f) return@Canvas
            val stroke = size.minDimension * 0.15f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val fraction = slice.value / total
                val sweep = 360f * fraction * progress.value
                val isSelected = selectedIndex == index
                val alpha = if (selectedIndex != null && !isSelected) 0.4f else 1f
                drawArc(
                    color = slice.color.copy(alpha = alpha),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = if (isSelected) stroke * 1.25f else stroke),
                )
                start += 360f * fraction
            }
        }
        centerContent?.invoke()
    }
}

/** Distribution bars — one thin, rounded bar per slice, height proportional to its value. */
@Composable
private fun ScBarDistribution(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false,
    labelEvery: Int = 1,
    selectedIndex: Int? = null,
    onSliceClick: ((Int) -> Unit)? = null,
    barAreaHeight: Dp = 150.dp,
) {
    val max: Float = slices.maxOfOrNull { it.value } ?: 1f
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight)
                .pointClickable(slices.size, onSliceClick),
        ) {
            drawBars(slices, max, selectedIndex)
        }
        if (showLabels) {
            AxisLabelsRow(points = slices, labelEvery = labelEvery)
        }
    }
}

/** Thin time-series bars with the usage duration above each non-zero bar. */
@Composable
private fun ScBarSeries(
    points: List<ChartSlice>,
    modifier: Modifier = Modifier,
    showValues: Boolean = false,
    valueFormatter: ((Float) -> String)? = null,
    labelEvery: Int = 1,
    valueEvery: Int = 1,
    labelLines: Int = 1,
    valueFontSp: Float = 8.5f,
    onPointTap: ((Int) -> Unit)? = null,
    selectedIndex: Int? = null,
    barAreaHeight: Dp = 150.dp,
) {
    val max: Float = points.maxOfOrNull { it.value } ?: 1f
    Column(modifier = modifier) {
        if (showValues) {
            ValuesRow(
                points = points,
                valueFormatter = valueFormatter,
                valueEvery = valueEvery,
                valueFontSp = valueFontSp,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight)
                .pointClickable(points.size, onPointTap),
        ) {
            drawBars(points, max, selectedIndex)
        }
        AxisLabelsRow(points = points, labelEvery = labelEvery, labelLines = labelLines)
    }
}

/** Professional line/area graph with point markers for time-series data. */
@Composable
private fun ScLineSeries(
    points: List<ChartSlice>,
    modifier: Modifier = Modifier,
    showValues: Boolean = false,
    valueFormatter: ((Float) -> String)? = null,
    labelEvery: Int = 1,
    valueEvery: Int = 1,
    labelLines: Int = 1,
    valueFontSp: Float = 8.5f,
    onPointTap: ((Int) -> Unit)? = null,
    onPointDrag: ((Int) -> Unit)? = null,
    selectedIndex: Int? = null,
    barAreaHeight: Dp = 150.dp,
) {
    val colors = LocalScColors.current
    val max: Float = points.maxOfOrNull { it.value } ?: 1f
    Column(modifier = modifier) {
        if (showValues) {
            ValuesRow(
                points = points,
                valueFormatter = valueFormatter,
                valueEvery = valueEvery,
                valueFontSp = valueFontSp,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight)
                .pointClickable(points.size, onPointTap)
                .pointDragDetector(points.size, onPointDrag),
        ) {
            if (points.size > 1) {
                val slot = size.width / points.size
                val pts = points.mapIndexed { index, point ->
                    Offset(
                        x = slot * index + slot / 2f,
                        y = size.height - size.height * 0.86f * (point.value / max),
                    )
                }
                // Soft area fill under the line.
                val area = Path().apply {
                    moveTo(pts.first().x, size.height)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, size.height)
                    close()
                }
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        listOf(colors.Accent.copy(alpha = 0.28f), colors.Accent.copy(alpha = 0f)),
                    ),
                )
                // Monitoring line — slightly thicker (4dp) slate gray so it
                // stands out against the background while staying subtle.
                val line = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = line,
                    color = MonitoringLineColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // Point markers — the selected one is enlarged and ringed so
                // the selected point is unmistakable.
                pts.forEachIndexed { index, point ->
                    val isSelected = selectedIndex == index
                    if (isSelected) {
                        drawCircle(color = colors.Accent, radius = 7.dp.toPx(), center = point)
                        drawCircle(color = colors.Bg, radius = 3.dp.toPx(), center = point)
                    } else {
                        drawCircle(
                            color = colors.Accent.copy(alpha = if (selectedIndex != null) 0.4f else 1f),
                            radius = 4.dp.toPx(),
                            center = point,
                        )
                    }
                }
                // Slide-to-inspect guides — ONE thin vertical line through the
                // selected point and ONE thin horizontal line toward the value
                // axis (left edge). Minimal, never stock-market style.
                selectedIndex?.let { sel ->
                    pts.getOrNull(sel)?.let { point ->
                        val guide = colors.TextDisabled.copy(alpha = 0.6f)
                        // Slightly thicker (2dp) so the interactive guides are
                        // clearly visible; color/alpha unchanged.
                        drawLine(
                            color = guide,
                            start = Offset(point.x, 0f),
                            end = Offset(point.x, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                        drawLine(
                            color = guide,
                            start = Offset(0f, point.y),
                            end = Offset(point.x, point.y),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
            }
        }
        AxisLabelsRow(points = points, labelEvery = labelEvery, labelLines = labelLines)
    }
}

/** Shared thin rounded bars — zero values draw nothing; selection dims the rest. */
private fun DrawScope.drawBars(slices: List<ChartSlice>, max: Float, selectedIndex: Int? = null) {
    val slot = size.width / slices.size
    val barWidth = slot * 0.30f
    slices.forEachIndexed { index, slice ->
        if (slice.value <= 0f) return@forEachIndexed
        val isSelected = selectedIndex == index
        val alpha = if (selectedIndex != null && !isSelected) 0.45f else 1f
        val barHeight = (size.height * 0.9f * (slice.value / max)).coerceAtLeast(3.dp.toPx())
        val x = slot * index + (slot - barWidth) / 2f
        val y = size.height - barHeight
        drawRoundRect(
            color = slice.color.copy(alpha = alpha),
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
        if (isSelected) {
            drawRoundRect(
                color = slice.color,
                topLeft = Offset(x - 2.dp.toPx(), y - 2.dp.toPx()),
                size = Size(barWidth + 4.dp.toPx(), barHeight + 4.dp.toPx()),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

/** Usage duration printed above each non-zero bar/point, aligned to slots. */
@Composable
private fun ValuesRow(
    points: List<ChartSlice>,
    valueFormatter: ((Float) -> String)?,
    valueEvery: Int = 1,
    valueFontSp: Float = 8.5f,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 3.dp),
    ) {
        points.forEachIndexed { index, point ->
            if (point.value > 0f && valueFormatter != null && (valueEvery <= 1 || index % valueEvery == 0)) {
                Text(
                    text = valueFormatter(point.value),
                    color = colors.TextSecondary,
                    fontSize = valueFontSp.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Sparse axis labels under the chart, aligned to the same slots. */
@Composable
private fun AxisLabelsRow(points: List<ChartSlice>, labelEvery: Int, labelLines: Int = 1) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        points.forEachIndexed { index, point ->
            if (labelEvery <= 1 || index % labelEvery == 0) {
                if (labelLines >= 2 && point.label.contains(' ')) {
                    // "12 AM" / "Mon Aug 4" → two stacked lines so dense
                    // timelines and day+date labels stay legible slot-by-slot.
                    val (first, second) = point.label.split(' ', limit = 2)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = first,
                            color = colors.TextSecondary,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = second,
                            color = colors.TextDisabled,
                            fontSize = 6.5.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = point.label,
                        color = colors.TextSecondary,
                        style = ScTextStyles.Caption.copy(fontSize = 9.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Tap-to-index modifier for the bar / line canvases. */
@Composable
private fun Modifier.pointClickable(
    pointCount: Int,
    onPointClick: ((Int) -> Unit)?,
): Modifier {
    if (onPointClick == null || pointCount <= 0) return this
    // rememberUpdatedState keeps the tap handler fresh across recompositions
    // even though the pointerInput block itself is keyed on pointCount only.
    val currentClick = rememberUpdatedState(onPointClick)
    return pointerInput(pointCount) {
        detectTapGestures { offset ->
            val slot = size.width / pointCount
            val index = (offset.x / slot).toInt().coerceIn(0, pointCount - 1)
            currentClick.value(index)
        }
    }
}

/**
 * Slide-to-inspect drag detector for the line chart. Reports the NEAREST
 * point continuously while the finger moves horizontally — the user never has
 * to hit a small dot precisely. The position change is intentionally NOT
 * consumed so vertical page scrolling still works when the drag starts on the
 * chart; only index changes are reported (no recomposition churn).
 */
@Composable
private fun Modifier.pointDragDetector(
    pointCount: Int,
    onPointDrag: ((Int) -> Unit)?,
): Modifier {
    if (onPointDrag == null || pointCount <= 0) return this
    val currentDrag = rememberUpdatedState(onPointDrag)
    return pointerInput(pointCount) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var lastIndex = -1
            fun selectAt(x: Float) {
                val slot = size.width / pointCount
                val index = (x / slot).toInt().coerceIn(0, pointCount - 1)
                if (index != lastIndex) {
                    lastIndex = index
                    currentDrag.value(index)
                }
            }
            selectAt(down.position.x)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                // Finger lifted (or the gesture was cancelled) — stop sliding.
                if (!change.pressed) break
                selectAt(change.position.x)
            }
        }
    }
}

/**
 * Hit-test a donut tap: returns the index of the slice whose arc contains
 * the tapped point (only taps inside the ring band count — the center is
 * reserved for the total overlay, the outside for the page behind it).
 */
private fun donutIndexAt(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    slices: List<ChartSlice>,
    total: Float,
): Int? {
    if (total <= 0f) return null
    val cx = width / 2f
    val cy = height / 2f
    val dx = x - cx
    val dy = y - cy
    val dist = sqrt(dx * dx + dy * dy)
    val stroke = min(width, height) * 0.15f
    // The drawn arc has diameter = min - stroke, so with the stroke centred
    // on it the ring spans radii [min/2 - stroke, min/2]. Match that exactly
    // so taps on the whole ring register and the centre stays reserved.
    val outerR = min(width, height) / 2f
    val innerR = outerR - stroke
    if (dist < innerR || dist > outerR) return null
    // Angle measured clockwise from the top, matching the -90° arc start.
    val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val angle = (angleDeg + 90f).mod(360f)
    var acc = 0f
    slices.forEachIndexed { index, slice ->
        val span = 360f * slice.value / total
        if (angle >= acc && angle < acc + span) return index
        acc += span
    }
    return null
}

/**
 * Small professional detail card shown when the user taps a bar / point /
 * donut slice — the exact DATE, clock TIME and USAGE for that point, in
 * every chart style. [timeRange] is the daily clock window
 * ("2:00 PM – 3:00 PM"); [actionLabel]/[onAction] offers a drill-down
 * (e.g. "View details" for a monthly date range); [onClose] dismisses.
 */
@Composable
fun ScPointTooltipCard(
    title: String,
    usage: String,
    timeRange: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.CardHover, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = colors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = ScTextStyles.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onClose != null) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = strings.chartTooltipClose,
                    tint = colors.TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
                )
            }
        }
        if (timeRange != null) {
            Text(
                text = "${strings.chartTooltipTime}: $timeRange",
                color = colors.TextSecondary,
                style = ScTextStyles.Caption,
            )
        }
        Text(
            text = "${strings.chartTooltipUsage}: $usage",
            color = colors.Accent,
            fontWeight = FontWeight.SemiBold,
            style = ScTextStyles.Caption,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = colors.Accent,
                fontWeight = FontWeight.SemiBold,
                style = ScTextStyles.Caption,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAction,
                    )
                    .padding(vertical = 2.dp),
            )
        }
    }
}

/**
 * Auto-fitting total for a donut center — starts at a size that suits short
 * totals and shrinks ONLY when needed so longer values ("27h 15m",
 * "124h 30m") always stay comfortably inside the inner circle. The text is
 * perfectly centered horizontally and vertically; the safe width is 70% of
 * the available container, which tracks the donut size on every screen.
 *
 * [total] is the headline value, [subtitle] the small line under it
 * (period label / date caption).
 */
@Composable
fun ScDonutCenterTotal(
    total: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val maxTextWidth = maxWidth * 0.7f
        var fontSize by remember(total) { mutableStateOf(26.sp) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = total,
                color = colors.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    // If the value is wider than the safe inner area, scale it
                    // down proportionally (small safety factor) and re-layout.
                    val widthDp = result.size.width / density.density
                    if (widthDp > maxTextWidth.value) {
                        val scaled = fontSize.value * (maxTextWidth.value / widthDp) * 0.98f
                        if (scaled < fontSize.value) fontSize = scaled.sp
                    }
                },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.TextSecondary,
                    style = ScTextStyles.Label,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * Compact legend for a time-distribution donut — one row per slice with its
 * colour dot, time/date label, exact duration and share. Rows are tappable
 * when [onSliceClick] is provided (e.g. selecting a slice to surface its
 * exact date/time). Duration is the primary information; percent secondary.
 *
 * [maxVisible] caps the rows shown BY DEFAULT (e.g. 4 for a daily hourly
 * timeline). When the legend has more entries than that, a Show More / Show
 * Less toggle appears right after the last visible row and expands / collapses
 * the list with a smooth size animation. The data is never filtered or
 * removed — only its initial visibility is controlled, and the toggle
 * disappears automatically when the legend has [maxVisible] or fewer entries.
 * null (the default) shows every row, keeping non-hourly legends unchanged.
 */
@Composable
fun ScTimeLegend(
    slices: List<ChartSlice>,
    valueFormatter: ((Float) -> String)? = null,
    onSliceClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    maxVisible: Int? = null,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val total: Float = slices.fold(0f) { acc, slice -> acc + slice.value }
    // Collapsible only when a cap is set AND there is actually more data to
    // reveal — 0, 1–4 entries never show the toggle. Keying `expanded` on the
    // slices list resets it per dataset (each day/period starts compact).
    var expanded by remember(slices) { mutableStateOf(false) }
    val visibleSlices = when {
        !expanded && maxVisible != null && maxVisible > 0 && slices.size > maxVisible -> slices.take(maxVisible)
        else -> slices
    }

    Column(
        modifier = modifier.animateContentSize(animationSpec = tween(220)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleSlices.forEachIndexed { index, slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onSliceClick != null) {
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSliceClick(index) },
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        } else Modifier,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(slice.color),
                )
                Text(
                    text = slice.label,
                    color = colors.TextSecondary,
                    style = ScTextStyles.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (valueFormatter != null) {
                    Text(
                        text = valueFormatter(slice.value),
                        color = colors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = ScTextStyles.Body,
                        maxLines = 1,
                    )
                }
                if (total > 0f) {
                    Text(
                        text = "${(slice.value / total * 100).roundToInt()}%",
                        color = colors.TextDisabled,
                        style = ScTextStyles.Caption,
                    )
                }
            }
        }

        if (maxVisible != null && maxVisible > 0 && slices.size > maxVisible) {
            // Show More / Show Less — the app's accent-text action styling,
            // centered so it reads as a natural part of the timeline.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = !expanded },
                    )
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (expanded) strings.chartShowLess else strings.chartShowMore,
                    color = colors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    style = ScTextStyles.Caption,
                )
            }
        }
    }
}
