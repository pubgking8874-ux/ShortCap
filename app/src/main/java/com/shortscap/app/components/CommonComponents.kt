package com.shortscap.app.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Mirrors .sc-switch / .sc-switch-knob — 42x24 track, animated knob position */
@Composable
fun ScSwitch(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    val t by animateFloatAsState(if (on) 1f else 0f, label = "switchTrack")
    val knobOffset by animateDpAsState(if (on) 21.dp else 3.dp, label = "switchKnob")
    val trackColor = lerpColor(colors.SwitchOffTrack, colors.Accent, t)

    Box(
        modifier = modifier
            .width(42.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() },
    ) {
        Box(
            Modifier
                .padding(top = 3.dp, start = knobOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/** Mirrors .sc-skeleton shimmer placeholder */
@Composable
fun ScSkeleton(height: Dp = 90.dp, modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.CardHover),
    )
}

/** Mirrors EmptyState({ icon, title, subtitle }) */
@Composable
fun ScEmptyState(icon: ImageVector, title: String, subtitle: String) {
    val colors = LocalScColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.Card)
                .border(1.dp, colors.Divider, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.TextDisabled, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = colors.TextSecondary, style = ScTextStyles.Label, textAlign = TextAlign.Center)
    }
}

/** Mirrors .sc-card / .sc-card.hoverable */
@Composable
fun ScCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalScColors.current
    val shape: Shape = RoundedCornerShape(22.dp)
    val base = modifier
        .clip(shape)
        .background(colors.Card, shape)
        .border(1.dp, colors.Divider, shape)
        .padding(18.dp)

    Column(
        modifier = if (onClick != null) {
            base.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        } else base,
        content = content,
    )
}

/** Mirrors StatCard({ icon, label, value, sub, accent }) used on Home */
@Composable
fun ScStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String? = null,
    accent: Color = LocalScColors.current.Accent,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    ScCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.StatIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(value, color = colors.TextPrimary, style = ScTextStyles.StatValue)
        Spacer(Modifier.height(2.dp))
        Text(label, color = colors.TextSecondary, style = ScTextStyles.Label)
        if (sub != null) {
            Spacer(Modifier.height(6.dp))
            Text(sub, color = colors.Success, style = ScTextStyles.Caption)
        }
    }
}

/** Mirrors .sc-chip / .sc-chip.active */
@Composable
fun ScChip(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (active) colors.ChipActiveBg else colors.Card, shape)
            .border(1.dp, if (active) colors.Accent else colors.Divider, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (active) colors.ChipActiveText else colors.TextSecondary, style = ScTextStyles.Caption)
    }
}

/** Mirrors .sc-btn.primary / .secondary / .danger */
enum class ScButtonVariant { PRIMARY, SECONDARY, DANGER }

@Composable
fun ScButton(
    label: String,
    variant: ScButtonVariant = ScButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val (bg, border, fg) = when (variant) {
        ScButtonVariant.PRIMARY -> Triple(colors.Accent, null as Color?, Color.White)
        ScButtonVariant.SECONDARY -> Triple(colors.Card, colors.Divider, colors.TextPrimary)
        ScButtonVariant.DANGER -> Triple(colors.DangerBtnBg, colors.DangerBtnBorder, colors.Danger)
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(vertical = 13.dp, horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, color = fg, style = ScTextStyles.ButtonLabel)
    }
}

/** Mirrors <hr className="sc-divider" /> */
@Composable
fun ScDivider(modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.Divider),
    )
}
