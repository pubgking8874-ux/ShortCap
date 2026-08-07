package com.shortscap.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Premium card shape used across the drawer pages (20–24dp rounded corners). */
private val PremiumCardShape: Shape = RoundedCornerShape(22.dp)

/**
 * Compact rounded-square icon container — 44dp tile with a 24dp icon inside
 * (icon ≈ 55–65% of the container) and generous internal padding, so the
 * tile reads as a small visual accent, not a big colored panel.
 */
private val PremiumIconTileShape: Shape = RoundedCornerShape(13.dp)

/**
 * Premium navigation card — compact icon tile + title + chevron, thin
 * border, soft press-scale animation and a soft blue border/glow while
 * pressed.
 *
 * Used for every clickable drill-down row on the drawer pages (Help & Support,
 * About ShortsCap and their sub-menus) and the Settings pages. By default it
 * contains ONLY the icon, title and arrow (no subtitle). Optional [subtitle]
 * and [trailing] slots let toggle rows (switch) and info rows (value) reuse
 * the same premium visual language — the chevron is replaced when a custom
 * trailing is provided.
 *
 * Compact proportions: ~70–84dp rows (44dp tile + tight padding) so more
 * settings fit on screen without scrolling.
 */
@Composable
fun ScPremiumNavCard(
    icon: ImageVector? = null,
    iconKey: IconKey? = null,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalScColors.current
    val style = LocalIconStyle.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Centralized icon resolution: a semantic [iconKey] resolves the vector,
    // tint and container from the active IconStyle; the legacy [icon] vector
    // param stays supported for non-category icons.
    val resolvedIcon = icon ?: iconKey?.let { IconTheme.icon(style, it) } ?: Icons.Filled.Info
    val iconTint = if (iconKey != null) IconTheme.tint(style, iconKey, colors.Accent) else colors.Accent
    // Compact neutral container (dark charcoal) — the color belongs to the icon.
    val iconBg = colors.CardHover

    val bg by animateColorAsState(
        targetValue = if (pressed) colors.Accent.copy(alpha = 0.10f) else colors.Card,
        label = "premiumCardBg",
    )
    val border by animateColorAsState(
        targetValue = if (pressed) colors.Accent.copy(alpha = 0.65f) else colors.Divider,
        label = "premiumCardBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "premiumCardScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (pressed) 12.dp else 2.dp,
                shape = PremiumCardShape,
                clip = false,
                ambientColor = colors.Accent.copy(alpha = 0.30f),
                spotColor = colors.Accent.copy(alpha = 0.20f),
            )
            .clip(PremiumCardShape)
            .background(bg, PremiumCardShape)
            .border(1.dp, border, PremiumCardShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PremiumIconTile(icon = resolvedIcon, tint = iconTint, bg = iconBg)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    color = colors.TextSecondary,
                    style = ScTextStyles.Body,
                    maxLines = 2,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Premium static info card — same visual language as [ScPremiumNavCard] but
 * non-interactive: large icon tile + title + optional description, with an
 * optional trailing slot (e.g. a "Future" pill on the Technologies page).
 */
@Composable
fun ScPremiumInfoCard(
    icon: ImageVector? = null,
    iconKey: IconKey? = null,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalScColors.current
    val style = LocalIconStyle.current
    val resolvedIcon = icon ?: iconKey?.let { IconTheme.icon(style, it) } ?: Icons.Filled.Info
    val iconTint = if (iconKey != null) IconTheme.tint(style, iconKey, colors.Accent) else colors.Accent
    // Compact neutral container (dark charcoal) — the color belongs to the icon.
    val iconBg = colors.CardHover
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PremiumCardShape)
            .background(colors.Card, PremiumCardShape)
            .border(1.dp, colors.Divider, PremiumCardShape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PremiumIconTile(icon = resolvedIcon, tint = iconTint, bg = iconBg)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = colors.TextSecondary, style = ScTextStyles.Body)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun PremiumIconTile(
    icon: ImageVector,
    tint: Color = LocalScColors.current.Accent,
    bg: Color = LocalScColors.current.CardHover,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(PremiumIconTileShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
