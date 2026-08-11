package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.permissions.PermissionId
import com.shortscap.app.permissions.PermissionInfo
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import java.text.DateFormat
import java.util.Date

/**
 * Permission detail page — a simple read-only view showing the permission's
 * status and purpose. Serves as the graceful fallback when Android exposes
 * no settings screen for a permission (the Permissions rows themselves open
 * the real Android settings pages directly). It never requests anything and
 * holds no action buttons. Status refreshes automatically on resume.
 */
@Composable
fun PermissionDetailScreen(
    permissionId: PermissionId,
    permission: PermissionInfo,
    onRefreshPermissions: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    RefreshPermissionsOnResume(onRefreshPermissions)

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = permissionTitle(permissionId, strings), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hero — icon + title + current status.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.Card, RoundedCornerShape(22.dp))
                    .border(1.dp, colors.Divider, RoundedCornerShape(22.dp))
                    .padding(vertical = 24.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val style = LocalIconStyle.current
                val iconKey = permissionIconKey(permissionId)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(colors.CardHover),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        permissionIcon(permissionId),
                        contentDescription = null,
                        tint = IconTheme.tint(style, iconKey, colors.Accent),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    permissionTitle(permissionId, strings),
                    color = colors.TextPrimary,
                    style = ScTextStyles.H1.copy(fontSize = 19.sp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    permissionStatusLabel(permission.status, strings),
                    color = permissionStatusColor(permission.status, colors),
                    style = ScTextStyles.BodySemiBold.copy(fontSize = 14.sp),
                    textAlign = TextAlign.Center,
                )
            }

            // Purpose — why this permission is required.
            DetailInfoCard(
                icon = Icons.Filled.Info,
                tint = colors.Accent,
                title = strings.permDetailWhyTitle,
                body = permissionDescription(permissionId, strings),
            )

            // Last checked.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.Card, RoundedCornerShape(22.dp))
                    .border(1.dp, colors.Divider, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = colors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    strings.permLastChecked,
                    color = colors.TextSecondary,
                    style = ScTextStyles.Body,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatLastChecked(permission.lastCheckedAt, strings.permNeverChecked),
                    color = colors.TextPrimary,
                    style = ScTextStyles.BodySemiBold.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

/** Premium info row — icon tile + title + body. */
@Composable
private fun DetailInfoCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.Card, RoundedCornerShape(22.dp))
            .border(1.dp, colors.Divider, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            )
            Spacer(Modifier.height(4.dp))
            Text(body, color = colors.TextSecondary, style = ScTextStyles.Body)
        }
    }
}

/** "Last checked" time formatting — local date+time, or "Never" if unknown. */
private fun formatLastChecked(lastCheckedAt: Long?, neverLabel: String): String {
    if (lastCheckedAt == null) return neverLabel
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastCheckedAt))
}
