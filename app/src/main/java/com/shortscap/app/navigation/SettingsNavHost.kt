package com.shortscap.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.model.SettingsDestination
import com.shortscap.app.screens.settings.AboutSettingsScreen
import com.shortscap.app.screens.settings.AllowedAppsScreen
import com.shortscap.app.screens.settings.AppearanceScreen
import com.shortscap.app.screens.settings.BlockedAppsScreen
import com.shortscap.app.screens.settings.MonitoringScheduleScreen
import com.shortscap.app.screens.settings.MonitoringScreen
import com.shortscap.app.screens.settings.NotificationsScreen
import com.shortscap.app.screens.settings.ResetAllScreen
import com.shortscap.app.screens.settings.SettingsSectionScreen
import com.shortscap.app.viewmodel.AppUiState
import com.shortscap.app.viewmodel.AppViewModel

/** Route constants for every Settings destination hosted by [SettingsNavHost]. */
object SettingsDestinations {
    const val GENERAL = "settings_general"
    const val MONITORING = "settings_monitoring"
    const val PERMISSIONS = "settings_permissions"
    const val NOTIFICATIONS = "settings_notifications"
    const val APPEARANCE = "settings_appearance"
    const val PRIVACY = "settings_privacy"
    const val DATA_BACKUP = "settings_data_backup"
    const val ABOUT = "settings_about"
    const val RESET_ALL = "settings_reset_all"

    const val BLOCKED_APPS = "settings_blocked_apps"
    const val ALLOWED_APPS = "settings_allowed_apps"
    const val SCHEDULE = "settings_schedule"
}

private fun SettingsDestination.startRoute(): String = when (this) {
    SettingsDestination.GENERAL -> SettingsDestinations.GENERAL
    SettingsDestination.MONITORING -> SettingsDestinations.MONITORING
    SettingsDestination.PERMISSIONS -> SettingsDestinations.PERMISSIONS
    SettingsDestination.NOTIFICATIONS -> SettingsDestinations.NOTIFICATIONS
    SettingsDestination.APPEARANCE -> SettingsDestinations.APPEARANCE
    SettingsDestination.PRIVACY -> SettingsDestinations.PRIVACY
    SettingsDestination.DATA_BACKUP -> SettingsDestinations.DATA_BACKUP
    SettingsDestination.ABOUT -> SettingsDestinations.ABOUT
    SettingsDestination.RESET_ALL -> SettingsDestinations.RESET_ALL
}

/**
 * Pops the settings back stack one level; at the root of the stack, closes
 * the overlay back to the Settings tab. Shared by the system Back handler
 * and every screen's top-bar back button, so both always navigate correctly
 * (a plain popBackStack() at the root silently does nothing).
 */
private fun NavHostController.backOrClose(onClose: () -> Unit) {
    if (!popBackStack()) onClose()
}

/**
 * Back-stack navigation for every Settings destination.
 *
 * Settings → <item> → sub-page (e.g. Monitoring → Blocked Apps). The system
 * Back button pops this NavHost's stack one level at a time; at the root of
 * the settings stack it closes the overlay back to the Settings tab. Back
 * never exits the app while a settings screen is open.
 */
@Composable
fun SettingsNavHost(
    startDestination: SettingsDestination,
    state: AppUiState,
    viewModel: AppViewModel,
    onClose: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination.startRoute(),
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 8 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 8 } },
    ) {
        // ---- Settings home destinations (each its own dedicated screen) ----
        composable(SettingsDestinations.GENERAL) {
            SettingsSectionScreen(
                icon = Icons.Filled.Tune,
                title = "General",
                description = "Language, sync and app defaults.",
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.MONITORING) {
            MonitoringScreen(
                settings = state.monitoring,
                onToggleMonitoring = viewModel::setMonitoringEnabled,
                onToggleAppBlocking = viewModel::setAppBlockingEnabled,
                onSetScreenTimeLimit = viewModel::setScreenTimeLimit,
                onToggleStrictMode = viewModel::setStrictMode,
                onTogglePlatform = viewModel::togglePlatform,
                onToggleBreakReminder = viewModel::setBreakReminderEnabled,
                onSetBreakReminderInterval = viewModel::setBreakReminderInterval,
                onOpenBlockedApps = { navController.navigate(SettingsDestinations.BLOCKED_APPS) },
                onOpenAllowedApps = { navController.navigate(SettingsDestinations.ALLOWED_APPS) },
                onOpenSchedule = { navController.navigate(SettingsDestinations.SCHEDULE) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.PERMISSIONS) {
            SettingsSectionScreen(
                icon = Icons.Filled.VerifiedUser,
                title = "Permissions",
                description = "Accessibility Service and Usage Access are required " +
                    "to monitor and block apps.",
                onBack = { navController.backOrClose(onClose) },
                extra = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ScPremiumInfoCard(
                            icon = Icons.Filled.AccessibilityNew,
                            title = "Accessibility Service",
                            subtitle = "Required to detect and block short-video apps.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ScPremiumInfoCard(
                            icon = Icons.Filled.DonutLarge,
                            title = "Usage Access",
                            subtitle = "Required to read screen-time statistics.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            )
        }

        composable(SettingsDestinations.NOTIFICATIONS) {
            NotificationsScreen(
                notificationsEnabled = state.notificationsEnabled,
                onToggleNotifications = viewModel::setNotifications,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.APPEARANCE) {
            AppearanceScreen(
                themeMode = state.themeMode,
                onThemeModeChange = viewModel::setThemeMode,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.PRIVACY) {
            SettingsSectionScreen(
                icon = Icons.Filled.Lock,
                title = "Privacy",
                description = "Data sharing and visibility preferences.",
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.DATA_BACKUP) {
            SettingsSectionScreen(
                icon = Icons.Filled.Storage,
                title = "Data Backup",
                description = "Cloud sync and export of your settings and data.",
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.ABOUT) {
            AboutSettingsScreen(onBack = { navController.backOrClose(onClose) })
        }

        composable(SettingsDestinations.RESET_ALL) {
            ResetAllScreen(
                onResetAll = {
                    viewModel.resetAllSettings()
                    navController.backOrClose(onClose)
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // ---- Monitoring sub-pages (UI only today) ----
        composable(SettingsDestinations.BLOCKED_APPS) {
            BlockedAppsScreen(onBack = { navController.backOrClose(onClose) })
        }
        composable(SettingsDestinations.ALLOWED_APPS) {
            AllowedAppsScreen(onBack = { navController.backOrClose(onClose) })
        }
        composable(SettingsDestinations.SCHEDULE) {
            MonitoringScheduleScreen(onBack = { navController.backOrClose(onClose) })
        }
    }

    // System Back while a settings screen is open: pop the settings back stack;
    // at its root, close the overlay back to the Settings tab. Never exits the
    // app (composed after the NavHost so it takes precedence).
    BackHandler {
        navController.backOrClose(onClose)
    }
}
