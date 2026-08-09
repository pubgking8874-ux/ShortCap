package com.shortscap.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Shared full-screen sub-screen chrome: a back button + title bar used by
 * every drawer sub-screen (Help & Support, About, Feedback, legal readers).
 * Matches the app top bar's icon-button styling (38dp, radius 12).
 *
 * [trailing] optionally renders custom content (e.g. a three-dot menu) in
 * place of the empty spacer at the far right of the bar; screens that don't
 * need it keep the balanced spacer.
 */
@Composable
fun ScSubScreenTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            color = colors.TextPrimary,
            style = ScTextStyles.LogoText,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                trailing()
            }
        } else {
            Box(modifier = Modifier.size(38.dp))
        }
    }
    ScDivider()
}