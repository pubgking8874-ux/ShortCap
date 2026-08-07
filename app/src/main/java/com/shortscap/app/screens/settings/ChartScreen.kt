package com.shortscap.app.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.charts.ChartStyle
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Localized chart-style name — also used as the Appearance row summary. */
fun ChartStyle.displayName(strings: AppStrings): String = when (this) {
    ChartStyle.BAR -> strings.chartBarChart
    ChartStyle.CIRCULAR -> strings.chartCircularChart
    ChartStyle.GRAPH -> strings.chartGraphChart
}

/**
 * Chart — the dedicated Chart Style page (Settings → Appearance → Chart).
 *
 * Exactly two options (Bar Chart / Circular Chart), each a premium row with
 * a mini chart preview, its name and a radio selector on the right. Browsing
 * does NOT change the app: only Apply persists the global preference (via
 * [onApply]) and returns to Appearance; Cancel discards the pending choice.
 * Apply is disabled while the pending style already matches the active one,
 * so there is never a pointless confirmation.
 *
 * The preference is GLOBAL — it drives the renderer for Activity, Web Usage
 * Analytics and every future analytics screen, and never touches the usage
 * data itself (see the charts package).
 */
@Composable
fun ChartScreen(
    currentStyle: ChartStyle,
    onApply: (ChartStyle) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var pending by remember { mutableStateOf(currentStyle) }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.appearanceChart, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChartOptionRow(
                style = ChartStyle.BAR,
                name = strings.chartBarChart,
                selected = pending == ChartStyle.BAR,
                onClick = { pending = ChartStyle.BAR },
            )
            ChartOptionRow(
                style = ChartStyle.CIRCULAR,
                name = strings.chartCircularChart,
                selected = pending == ChartStyle.CIRCULAR,
                onClick = { pending = ChartStyle.CIRCULAR },
            )
            ChartOptionRow(
                style = ChartStyle.GRAPH,
                name = strings.chartGraphChart,
                selected = pending == ChartStyle.GRAPH,
                onClick = { pending = ChartStyle.GRAPH },
            )
        }

        // Bottom action bar — Cancel discards, Apply persists + updates the
        // global chart renderer preference. navigationBarsPadding lifts the
        // buttons clear of the Android system navigation area (0 inset on
        // gesture nav, ~48dp on 3-button nav) so they never sit under it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScButton(
                label = strings.cancel,
                variant = ScButtonVariant.SECONDARY,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            ScButton(
                label = strings.apply,
                variant = ScButtonVariant.PRIMARY,
                enabled = pending != currentStyle,
                onClick = {
                    onApply(pending)
                    onBack()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Premium selectable chart option — mini chart preview + name + radio
 * selector. The whole row is tappable (radio role for accessibility); the
 * selected row gets the ShortsCap-blue border/tint and a soft press-scale.
 */
@Composable
private fun ChartOptionRow(
    style: ChartStyle,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)

    val bg by animateColorAsState(
        targetValue = when {
            pressed -> colors.Accent.copy(alpha = 0.10f)
            selected -> colors.ChipActiveBg
            else -> colors.Card
        },
        label = "chartRowBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> colors.Accent.copy(alpha = 0.60f)
            pressed -> colors.Accent.copy(alpha = 0.40f)
            else -> colors.Divider
        },
        label = "chartRowBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chartRowScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (pressed) 10.dp else 2.dp,
                shape = shape,
                clip = false,
            )
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Compact mini chart preview representing this chart type.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            ChartPreviewGlyph(style = style)
        }
        Text(
            name,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Tiny original chart glyph (bars or donut) drawn inside the option tile. */
@Composable
private fun ChartPreviewGlyph(style: ChartStyle) {
    val colors = LocalScColors.current
    Canvas(modifier = Modifier.size(26.dp)) {
        when (style) {
            ChartStyle.BAR -> {
                val heights = listOf(0.35f, 0.65f, 0.45f, 0.85f)
                val slot = size.width / heights.size
                val barWidth = slot * 0.55f
                heights.forEachIndexed { index, height ->
                    val barHeight = size.height * height
                    drawRoundRect(
                        color = if (index % 2 == 0) colors.Accent else colors.Accent.copy(alpha = 0.45f),
                        topLeft = Offset(slot * index + (slot - barWidth) / 2f, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }
            ChartStyle.CIRCULAR -> {
                val stroke = size.minDimension * 0.16f
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                val arcs = listOf(
                    colors.Accent to 160f,
                    colors.Success to 120f,
                    colors.Warning to 80f,
                )
                var start = -90f
                arcs.forEach { (color, sweep) ->
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            ChartStyle.GRAPH -> {
                // Mini line/area glyph: baseline, rising accent line, dots.
                val pts = listOf(
                    Offset(size.width * 0.10f, size.height * 0.72f),
                    Offset(size.width * 0.32f, size.height * 0.40f),
                    Offset(size.width * 0.54f, size.height * 0.58f),
                    Offset(size.width * 0.76f, size.height * 0.26f),
                    Offset(size.width * 0.90f, size.height * 0.42f),
                )
                val area = Path().apply {
                    moveTo(pts.first().x, size.height * 0.92f)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, size.height * 0.92f)
                    close()
                }
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        listOf(colors.Accent.copy(alpha = 0.30f), colors.Accent.copy(alpha = 0f)),
                    ),
                )
                val line = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = line,
                    color = colors.Accent,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                pts.forEach { drawCircle(color = colors.Accent, radius = 2.5.dp.toPx(), center = it) }
            }
        }
    }
}
