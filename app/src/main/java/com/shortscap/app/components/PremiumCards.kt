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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Premium card shape used across the drawer pages (20–24dp rounded corners). */
private val PremiumCardShape: Shape = RoundedCornerShape(22.dp)

/** Rounded tile that holds the large card icon (36dp icon inside a 60dp tile). */
private val PremiumIconTileShape: Shape = RoundedCornerShape(18.dp)

/**
 * Premium navigation card — large icon tile + title + chevron, thin border,
 * soft press-scale animation and a soft blue border/glow while pressed.
 *
 * Used for every clickable drill-down row on the drawer pages (Help & Support,
 * About ShortsCap and their sub-menus). Contains ONLY the icon, title and
 * arrow — no subtitle — per the premium menu design.
 */
@Composable
fun ScPremiumNavCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

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
                elevation = if (pressed) 14.dp else 3.dp,
                shape = PremiumCardShape,
                clip = false,
                ambientColor = colors.Accent.copy(alpha = 0.35f),
                spotColor = colors.Accent.copy(alpha = 0.25f),
            )
            .clip(PremiumCardShape)
            .background(bg, PremiumCardShape)
            .border(1.dp, border, PremiumCardShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PremiumIconTile(icon)
        Text(
            title,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Premium static info card — same visual language as [ScPremiumNavCard] but
 * non-interactive: large icon tile + title + optional description, with an
 * optional trailing slot (e.g. a "Future" pill on the Technologies page).
 */
@Composable
fun ScPremiumInfoCard(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalScColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PremiumCardShape)
            .background(colors.Card, PremiumCardShape)
            .border(1.dp, colors.Divider, PremiumCardShape)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PremiumIconTile(icon)
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(PremiumIconTileShape)
            .background(LocalScColors.current.StatIconBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = LocalScColors.current.Accent,
            modifier = Modifier.size(36.dp),
        )
    }
}
