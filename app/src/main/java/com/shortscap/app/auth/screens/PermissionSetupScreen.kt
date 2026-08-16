package com.shortscap.app.auth.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.components.ScDivider
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.permissions.PermissionActions
import com.shortscap.app.permissions.PermissionId
import com.shortscap.app.permissions.PermissionInfo
import com.shortscap.app.permissions.PermissionRepository
import com.shortscap.app.permissions.PermissionStatus
import com.shortscap.app.permissions.SetupRequiredPermissionIds
import com.shortscap.app.screens.settings.RefreshPermissionsOnResume
import com.shortscap.app.screens.settings.permissionDescription
import com.shortscap.app.screens.settings.permissionIcon
import com.shortscap.app.screens.settings.permissionTitle
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScColors

/**
 * First-launch Permission Setup — the gate between Splash and the
 * authentication flow (Splash → Permission Setup → Login/Create Account/Guest).
 *
 * Shown ONLY on a fresh installation (once — completion is persisted in
 * [com.shortscap.app.permissions.FirstLaunchSetupStore], so it never re-appears
 * on later launches, force-stops or reboots). It is a centralized
 * permission-orchestration flow that checks every permission the current
 * ShortsCap engines actually require ([SetupRequiredPermissionIds]):
 *
 *  - USAGE_ACCESS   → Screen Activity / general app-usage collection
 *  - ACCESSIBILITY  → foreground observation (Shorts detection + Screen Activity)
 *  - OVERLAY        → Shorts HUD overlay
 *  - NOTIFICATIONS  → Study Mode / Shorts limit alerts
 *
 * Every status comes from the REAL Android OS ([PermissionRepository] — the
 * same seam the Settings → Permissions screen uses, so the two can never
 * disagree). Statuses re-check automatically on every resume, i.e. after the
 * user returns from an Android system-settings page.
 *
 * Behavior per permission:
 *  - Special permissions (Usage Access, Accessibility, Overlay): tapping the
 *    row explains it here, opens the correct Android system-settings page
 *    ([PermissionActions]), and re-checks on return — never a fake in-app grant.
 *  - Notifications: on Android 13+ the standard Android runtime dialog is
 *    used ([ActivityResultContracts.RequestPermission]); below that it opens
 *    the app's notification settings page.
 *
 * "Continue" stays disabled until ALL required permissions are actually
 * granted ("All required permissions are ready"). Already-granted permissions
 * are never requested again. Engine isolation is preserved: this gate only
 * checks the required set — it never disables Shorts Control because Screen
 * Activity is off, or vice versa.
 */
@Composable
fun PermissionSetupScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    var permissions by remember {
        mutableStateOf(
            PermissionRepository.checkAll(context, PermissionRepository.seedPermissions()),
        )
    }
    fun refresh() {
        permissions = PermissionRepository.checkAll(context, permissions)
    }
    // Re-check on first open AND after returning from Android Settings —
    // same lifecycle seam as the Settings → Permissions screen.
    RefreshPermissionsOnResume { refresh() }

    // Notifications on Android 13+ uses the standard runtime dialog.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }

    val required = permissions.filter { it.id in SetupRequiredPermissionIds }
    val allGranted = required.all { it.status == PermissionStatus.GRANTED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Set Up Permissions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.TextPrimary,
            )
            Text(
                "ShortsCap needs a few permissions to monitor your Shorts usage, " +
                    "show the HUD overlay and send reminders. Tap each one to " +
                    "grant it in Android Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.TextSecondary,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.Card, RoundedCornerShape(22.dp))
                    .border(1.dp, colors.Divider, RoundedCornerShape(22.dp)),
            ) {
                required.forEachIndexed { index, info ->
                    if (index > 0) ScDivider(modifier = Modifier.padding(start = 68.dp))
                    PermissionSetupRow(
                        info = info,
                        onClick = {
                            val granted = info.status == PermissionStatus.GRANTED
                            when (info.id) {
                                // Standard Android runtime dialog where one exists
                                // (Android 13+); system settings page otherwise.
                                PermissionId.NOTIFICATIONS -> {
                                    if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        PermissionActions.open(context, info.id)
                                    }
                                }
                                // Special permissions: explain + open the correct
                                // Android system-settings page, re-check on return.
                                else -> PermissionActions.open(context, info.id)
                            }
                        },
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            AnimatedVisibility(
                visible = allGranted,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = colors.Success,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "All required permissions are ready",
                        color = colors.Success,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            AuthPrimaryButton(
                text = "Continue",
                onClick = onContinue,
                enabled = allGranted,
            )
        }
    }
}

/**
 * One required-permission row: icon tile · name + purpose · live status
 * (Granted / Action Required / Not Granted). The ENTIRE row is the tap
 * target — it grants via the standard runtime dialog where one exists
 * (notifications on Android 13+) or opens the correct Android system-settings
 * page. Statuses are informational only; the real OS is the source of truth.
 */
@Composable
private fun PermissionSetupRow(
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
        label = "permissionSetupRowBg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
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
                tint = colors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                permissionTitle(info.id, strings),
                color = colors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                permissionDescription(info.id, strings),
                color = colors.TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            setupStatusLabel(info.status),
            color = setupStatusColor(info.status, colors),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Setup-screen status vocabulary — the three states required by the flow:
 * "Granted" when the permission/service is active, "Action Required" when it
 * is missing and the user must grant it (the whole point of this screen),
 * "Not Granted" for a denied/inactive state.
 */
private fun setupStatusLabel(status: PermissionStatus): String = when (status) {
    PermissionStatus.GRANTED -> "Granted"
    PermissionStatus.NOT_GRANTED -> "Action Required"
    PermissionStatus.DISABLED -> "Not Granted"
}

private fun setupStatusColor(status: PermissionStatus, colors: ScColors): Color = when (status) {
    PermissionStatus.GRANTED -> colors.Success
    PermissionStatus.NOT_GRANTED -> colors.Warning
    PermissionStatus.DISABLED -> colors.Danger
}
