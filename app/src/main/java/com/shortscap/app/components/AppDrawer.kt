package com.shortscap.app.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.model.DrawerItem
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Mirrors:
 *   .sc-overlay / .sc-overlay.open  -> scrim, fades in/out (opacity transition .25s)
 *   .sc-drawer  / .sc-drawer.open   -> 75% width panel, slides from translateX(-105%) to 0,
 *                                      cubic-bezier(0.16,1,0.3,1) easing over .28s
 * Item list, header, and footer (version string) preserved verbatim.
 */
@Composable
fun ScAppDrawer(
    open: Boolean,
    onClose: () -> Unit,
    items: List<DrawerItem>,
    logoIcon: @Composable () -> Unit,
) {
    val colors = LocalScColors.current
    val scrimAlpha by animateFloatAsState(if (open) 0.45f else 0f, animationSpec = tween(250), label = "scrim")
    val drawerWidth = 300.dp
    val offsetX by animateDpAsState(
        targetValue = if (open) 0.dp else -(drawerWidth + 8.dp),
        animationSpec = tween(280),
        label = "drawerOffset",
    )

    if (scrimAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(drawerWidth)
            .offset(x = offsetX)
            .background(colors.DrawerBg, RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(vertical = 22.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                logoIcon()
                Column {
                    Text("ShortsCap", color = colors.TextPrimary, style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp))
                    Text("Digital Wellbeing", color = colors.TextSecondary, style = ScTextStyles.Caption)
                }
            }
            ScDivider(modifier = Modifier.padding(horizontal = 0.dp))
            Spacer(Modifier.height(8.dp))

            // Items
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(item.icon, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(19.dp))
                    Text(item.label, color = colors.TextPrimary, style = ScTextStyles.Body)
                }
                if (index < items.size - 1) {
                    ScDivider(modifier = Modifier.padding(horizontal = 22.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent),
            ) {
                ScDivider()
                Text(
                    "ShortsCap v2.4.1 · Build 2026072801\n© 2026 ShortsCap. All rights reserved.",
                    color = colors.TextDisabled,
                    style = ScTextStyles.Caption.copy(fontSize = 11.sp),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                )
            }
        }
    }
}
