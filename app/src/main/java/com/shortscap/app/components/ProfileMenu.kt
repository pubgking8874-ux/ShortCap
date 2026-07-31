package com.shortscap.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.model.ProfileMenuItem
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Mirrors:
 *   .sc-popover / .sc-popover.open -> 220dp wide card anchored below the top bar,
 *   fade + translateY(-6px) scale(0.97) -> identity, .18s ease-out.
 */
@Composable
fun ScProfileMenu(
    open: Boolean,
    onClose: () -> Unit,
    items: List<ProfileMenuItem>,
    onItemClick: (ProfileMenuItem) -> Unit = {},
) {
    val colors = LocalScColors.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.97f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.97f),
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 16.dp)
                    .width(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.Card, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.Divider, RoundedCornerShape(16.dp)),
            ) {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onItemClick(item)
                                onClose()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (item.isDanger) colors.Danger else colors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            item.label,
                            color = if (item.isDanger) colors.Danger else colors.TextPrimary,
                            style = ScTextStyles.Body.copy(fontSize = 13.5.sp),
                        )
                    }
                    if (index == items.size - 2) ScDivider()
                }
            }
        }
    }
}
