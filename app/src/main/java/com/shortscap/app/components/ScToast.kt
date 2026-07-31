package com.shortscap.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Mirrors .sc-toast / .sc-toast.show — floats above the nav bar, centered pill, fade+slide up */
@Composable
fun BoxScope.ScToast(message: String?, checkIcon: ImageVector) {
    val colors = LocalScColors.current
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 100.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(colors.ToastBg, RoundedCornerShape(14.dp))
                .border(1.dp, colors.Divider, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(checkIcon, contentDescription = null, tint = colors.Success, modifier = Modifier.size(15.dp))
            Text(message ?: "", color = colors.ToastText, style = ScTextStyles.Body)
        }
    }
}
