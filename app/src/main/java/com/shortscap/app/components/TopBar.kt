package com.shortscap.app.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Mirrors .sc-iconbtn (38x38 tappable icon button with ripple, radius 12) */
@Composable
fun ScIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    val colors = LocalScColors.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.TextPrimary, modifier = Modifier.size(22.dp))
    }
}

/**
 * Mirrors:
 * function TopBar({ onMenu, onProfile }) { ... hamburger | logo | avatar+dot ... }
 * 60dp height, bottom divider, translucent/blurred background.
 */
@Composable
fun ScTopBar(
    onMenu: () -> Unit,
    onProfile: () -> Unit,
    menuIcon: ImageVector,
    userIcon: ImageVector,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.Bg.copy(alpha = 0.92f))
            .border(width = 0.dp, color = Color.Transparent)
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ScIconButton(icon = menuIcon, contentDescription = "Menu", onClick = onMenu)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors.Accent, colors.Accent2))),
                contentAlignment = Alignment.Center,
            ) {
                Text("S", color = Color.Black, style = ScTextStyles.BodySemiBold)
            }
            Text("ShortsCap", color = colors.TextPrimary, style = ScTextStyles.LogoText)
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .background(colors.Card, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onProfile() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(userIcon, contentDescription = "Profile", tint = colors.TextPrimary, modifier = Modifier.size(17.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.Success)
                    .border(2.dp, colors.Bg, CircleShape),
            )
        }
    }
    ScDivider()
}
