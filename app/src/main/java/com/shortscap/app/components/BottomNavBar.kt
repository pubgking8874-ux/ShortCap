package com.shortscap.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.model.ScScreen
import com.shortscap.app.theme.LocalScColors

/** Bottom-nav item — the icon is resolved through the centralized icon
 *  system ([IconKey] + active [IconStyle]) instead of a hardcoded vector. */
data class NavItemSpec(val screen: ScScreen, val iconKey: IconKey)

/**
 * Mirrors .sc-bottomnav / .sc-navbtn / .sc-navbtn.active — floating pill,
 * 4 circular 52dp buttons (Material minimum touch target), active item gets
 * accent background + shadow.
 */
@Composable
fun ScBottomNav(
    current: ScScreen,
    onSelect: (ScScreen) -> Unit,
    items: List<NavItemSpec>,
) {
    val colors = LocalScColors.current
    val style = LocalIconStyle.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(colors.Card.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
            .border(1.dp, colors.Divider, RoundedCornerShape(999.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { spec ->
            val active = spec.screen == current
            val icon = IconTheme.icon(style, spec.iconKey)
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(spec.screen) },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.Accent else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = spec.screen.name,
                        tint = if (active) Color.White else colors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
