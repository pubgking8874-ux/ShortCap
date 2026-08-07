package com.shortscap.app.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.permissions.PermissionActions
import com.shortscap.app.permissions.PermissionId
import com.shortscap.app.permissions.PermissionInfo
import com.shortscap.app.permissions.PermissionStatus
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Permissions — clean status overview, NOT a permission-request page.
 *
 * Permissions are requested during first-time onboarding; this page only
 * reflects the current state. Each permission is a compact settings row
 * (icon · name · colored status · chevron) — no cards, no big buttons.
 *
 * Tap behavior:
 *  - Already granted → opens a simple detail page (status + purpose).
 *  - Not granted / disabled → opens the corresponding Android Settings screen
 *    directly so the user can enable it there.
 *
 * Statuses are detected automatically on every resume (first open AND after
 * returning from Android Settings) — no manual refresh button.
 */
@Composable
fun PermissionsScreen(
    permissions: List<PermissionInfo>,
    onRefreshPermissions: () -> Unit,
    onOpenDetail: (PermissionId) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    RefreshPermissionsOnResume(onRefreshPermissions)

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.permissionsTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // One grouped settings container (like modern Android settings)
            // with thin dividers between the rows — not individual cards.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.Card, RoundedCornerShape(22.dp))
                    .border(1.dp, colors.Divider, RoundedCornerShape(22.dp)),
            ) {
                PermissionId.entries.forEachIndexed { index, id ->
                    val info = permissions.firstOrNull { it.id == id } ?: PermissionInfo(id = id)
                    if (index > 0) ScDivider(modifier = Modifier.padding(start = 68.dp))
                    PermissionRow(
                        info = info,
                        onClick = {
                            when (info.status) {
                                PermissionStatus.GRANTED,
                                PermissionStatus.FUTURE,
                                PermissionStatus.NOT_AVAILABLE,
                                -> onOpenDetail(id)
                                else -> PermissionActions.open(context, id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Compact settings row — icon tile + permission name + colored status text +
 * chevron. Sits inside the grouped container with a divider above/below;
 * only a soft accent tint highlights the row while pressed.
 */
@Composable
private fun PermissionRow(
    info: PermissionInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val bg by animateColorAsState(
        targetValue = if (pressed) colors.Accent.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(120),
        label = "permissionRowBg",
    )

    // The permission icon sits in a compact neutral tile; its category color
    // belongs to the ICON (permission rows keep their own recognizable icon).
    val style = LocalIconStyle.current
    val iconKey = permissionIconKey(info.id)
    Row(
        modifier = modifier
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                permissionIcon(info.id),
                contentDescription = null,
                tint = IconTheme.tint(style, iconKey, colors.Accent),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            permissionTitle(info.id, strings),
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            permissionStatusLabel(info.id, info.status, strings),
            color = permissionStatusColor(info.status, colors),
            style = ScTextStyles.BodySemiBold.copy(fontSize = 13.sp),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.TextDisabled,
            modifier = Modifier.size(18.dp),
        )
    }
}
