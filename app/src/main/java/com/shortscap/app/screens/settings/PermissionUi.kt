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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.icons.IconKey
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
    PermissionId.STORAGE_MEDIA -> Icons.Filled.PhotoLibrary
    PermissionId.SYSTEM_AUDIO_ACCESS -> Icons.Filled.VolumeUp
}

/**
 * Semantic icon key for a permission — lets the centralized icon system
 * color each permission row with its own category color in the Vibrant
 * style (the icon vector itself stays [permissionIcon]).
 */
fun permissionIconKey(id: PermissionId): IconKey = when (id) {
    PermissionId.USAGE_ACCESS -> IconKey.PERM_USAGE_ACCESS
    PermissionId.ACCESSIBILITY -> IconKey.PERM_ACCESSIBILITY
    PermissionId.OVERLAY -> IconKey.PERM_OVERLAY
    PermissionId.NOTIFICATIONS -> IconKey.PERM_NOTIFICATIONS
    PermissionId.BATTERY_OPTIMIZATION -> IconKey.PERM_BATTERY
    PermissionId.STORAGE_MEDIA -> IconKey.PERM_STORAGE
    PermissionId.SYSTEM_AUDIO_ACCESS -> IconKey.PERM_SYSTEM_AUDIO
}

/** Localized title for a permission. */
fun permissionTitle(id: PermissionId, strings: AppStrings): String = when (id) {
    PermissionId.USAGE_ACCESS -> strings.permUsageAccess
    PermissionId.ACCESSIBILITY -> strings.permAccessibility
    PermissionId.OVERLAY -> strings.permOverlay
    PermissionId.NOTIFICATIONS -> strings.permNotifications
    PermissionId.BATTERY_OPTIMIZATION -> strings.permBattery
    PermissionId.STORAGE_MEDIA -> strings.permStorage
    PermissionId.SYSTEM_AUDIO_ACCESS -> strings.permSystemAudioAccess
}

/** Localized short description (purpose) for a permission. */
fun permissionDescription(id: PermissionId, strings: AppStrings): String = when (id) {
    PermissionId.USAGE_ACCESS -> strings.permUsageAccessDesc
    PermissionId.ACCESSIBILITY -> strings.permAccessibilityDesc
    PermissionId.OVERLAY -> strings.permOverlayDesc
    PermissionId.NOTIFICATIONS -> strings.permNotificationsDesc
    PermissionId.BATTERY_OPTIMIZATION -> strings.permBatteryDesc
    PermissionId.STORAGE_MEDIA -> strings.permStorageDesc
    PermissionId.SYSTEM_AUDIO_ACCESS -> strings.permSystemAudioAccessDesc
}

/**
 * Normalized UI status text — the ONLY two statuses shown app-wide for
 * permissions: "Enabled" when the permission/service is active and working,
 * "Disabled" when it is missing, denied, or inactive. Permissions may
 * internally have different Android states ([PermissionStatus]) and the
 * underlying checks are unchanged, but the visible UI normalizes them all
 * into this single consistent vocabulary. Nothing else is displayed as a
 * permission status.
 */
fun permissionStatusLabel(status: PermissionStatus, strings: AppStrings): String =
    when (status) {
        PermissionStatus.GRANTED -> strings.permStatusEnabled
        PermissionStatus.NOT_GRANTED,
        PermissionStatus.DISABLED,
        -> strings.permStatusDisabled
    }

/**
 * Status color: green = enabled, orange = disabled (not granted), red =
 * disabled (denied).
 */
fun permissionStatusColor(status: PermissionStatus, colors: ScColors): Color = when (status) {
    PermissionStatus.GRANTED -> colors.Success
    PermissionStatus.NOT_GRANTED -> colors.Warning
    PermissionStatus.DISABLED -> colors.Danger
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
