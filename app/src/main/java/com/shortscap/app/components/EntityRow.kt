package com.shortscap.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shortscap.app.model.ScEntity
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Reusable list row for any app/website [ScEntity]: leading icon, title,
 * optional subtitle, optional usage/restriction info, and a trailing slot.
 * Used by Home Recent Activity, the Web site lists, and any future list
 * (search results, history, analytics) — no duplicate row implementations.
 */
@Composable
fun ScEntityRow(
    entity: ScEntity,
    subtitle: String? = null,
    usageInfo: String? = null,
    restrictionStatus: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    restrictionColor: Color? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalScColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier,
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScEntityIcon(entity = entity)
        Column(modifier = Modifier.weight(1f)) {
            Text(entity.title, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
            if (subtitle != null) {
                Text(subtitle, color = colors.TextSecondary, style = ScTextStyles.Caption, maxLines = 1)
            }
            if (usageInfo != null || restrictionStatus != null) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (usageInfo != null) {
                        Text(usageInfo, color = colors.TextSecondary, style = ScTextStyles.Caption)
                    }
                    if (restrictionStatus != null) {
                        Text(restrictionStatus, color = restrictionColor ?: colors.Success, style = ScTextStyles.Caption)
                    }
                }
            }
        }
        trailing()
    }
}
