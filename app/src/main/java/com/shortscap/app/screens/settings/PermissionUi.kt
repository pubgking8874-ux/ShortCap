package com.shortscap.app.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.permissions.PermissionId
import com.shortscap.app.permissions.PermissionStatus
import com.shortscap.app.theme.ScColors

/** Leading icon for a permission row / detail page. */
fun permissionIcon(id: PermissionId): ImageVector = when (id) {
    PermissionId.USAGE_ACCESS -> Icons.Filled.DonutLarge
    PermissionId.ACCESSIBILITY -> Icons.Filled.AccessibilityNew
    PermissionId.OVERLAY -> Icons.Filled.Layers
    PermissionId.NOTIFICATIONS -> Icons.Filled.NotificationsActive
    PermissionId.BATTERY_OPTIMIZATION -> Icons.Filled.BatteryChargingFull
    PermissionId.AUTO_START -> Icons.Filled.PowerSettingsNew
    PermissionId.STORAGE_MEDIA -> Icons.Filled.PhotoLibrary
    PermissionId.ROOT -> Icons.Filled.Security
}

/** Localized title for a permission. */
fun permissionTitle(id: PermissionId, strings: AppStrings): String = when (id) {
    PermissionId.USAGE_ACCESS -> strings.permUsageAccess
    PermissionId.ACCESSIBILITY -> strings.permAccessibility
    PermissionId.OVERLAY -> strings.permOverlay
    PermissionId.NOTIFICATIONS -> strings.permNotifications
    PermissionId.BATTERY_OPTIMIZATION -> strings.permBattery
    PermissionId.AUTO_START -> strings.permAutoStart
    PermissionId.STORAGE_MEDIA -> strings.permStorage
    PermissionId.ROOT -> strings.permRoot
}

/** Localized short description (purpose) for a permission. */
fun permissionDescription(id: PermissionId, strings: AppStrings): String = when (id) {
    PermissionId.USAGE_ACCESS -> strings.permUsageAccessDesc
    PermissionId.ACCESSIBILITY -> strings.permAccessibilityDesc
    PermissionId.OVERLAY -> strings.permOverlayDesc
    PermissionId.NOTIFICATIONS -> strings.permNotificationsDesc
    PermissionId.BATTERY_OPTIMIZATION -> strings.permBatteryDesc
    PermissionId.AUTO_START -> strings.permAutoStartDesc
    PermissionId.STORAGE_MEDIA -> strings.permStorageDesc
    PermissionId.ROOT -> strings.permRootDesc
}

/**
 * Status text shown next to each permission. Granted state uses a
 * permission-specific label (Granted / Enabled / Allowed / Ignored); the
 * remaining states use the generic vocabulary.
 */
fun permissionStatusLabel(id: PermissionId, status: PermissionStatus, strings: AppStrings): String =
    when (status) {
        PermissionStatus.GRANTED -> when (id) {
            PermissionId.ACCESSIBILITY -> strings.permStatusEnabled
            PermissionId.NOTIFICATIONS, PermissionId.STORAGE_MEDIA -> strings.permStatusAllowed
            PermissionId.BATTERY_OPTIMIZATION -> strings.permStatusIgnored
            else -> strings.permStatusGranted // USAGE_ACCESS, OVERLAY
        }
        PermissionStatus.NOT_GRANTED -> strings.permStatusNeedsAttention
        PermissionStatus.DISABLED -> strings.permStatusDenied
        PermissionStatus.FUTURE -> strings.permStatusFuture
        PermissionStatus.NOT_AVAILABLE -> strings.permStatusNotAvailable
    }

/**
 * Status color: green = granted, orange = needs attention, red = denied,
 * gray = future / not available.
 */
fun permissionStatusColor(status: PermissionStatus, colors: ScColors): Color = when (status) {
    PermissionStatus.GRANTED -> colors.Success
    PermissionStatus.NOT_GRANTED -> colors.Warning
    PermissionStatus.DISABLED -> colors.Danger
    PermissionStatus.FUTURE, PermissionStatus.NOT_AVAILABLE -> colors.TextDisabled
}

/**
 * Auto-refreshes permission statuses whenever the screen is visible — covers
 * both first open AND returning from Android Settings after granting /
 * revoking a permission. No manual refresh button required.
 *
 * Note: a plain ON_RESUME observer is not enough for in-app navigation
 * (Settings → Permissions stays within the activity, so the entry's lifecycle
 * may already be RESUMED and no transition event fires). Refreshing
 * immediately when the state is already RESUMED covers that first-open case.
 */
@Composable
fun RefreshPermissionsOnResume(onRefresh: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            onRefresh()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
