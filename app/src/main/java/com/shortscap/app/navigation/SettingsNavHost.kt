package com.shortscap.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.SettingsDestination
import com.shortscap.app.notifications.NotificationCategory
import com.shortscap.app.permissions.PermissionId
import com.shortscap.app.permissions.PermissionRepository
import com.shortscap.app.screens.legal.LegalDocument
import com.shortscap.app.screens.legal.LegalDocumentScreen
import com.shortscap.app.screens.settings.AboutSettingsScreen
import com.shortscap.app.screens.settings.AllowedAppsScreen
import com.shortscap.app.screens.settings.AppearanceScreen
import com.shortscap.app.screens.settings.BlockedAppsScreen
import com.shortscap.app.screens.settings.GeneralScreen
import com.shortscap.app.screens.settings.IconScreen
import com.shortscap.app.screens.settings.LanguageScreen
import com.shortscap.app.screens.settings.MonitoringScheduleScreen
import com.shortscap.app.screens.settings.MonitoringScreen
import com.shortscap.app.screens.settings.NotificationCategoryScreen
import com.shortscap.app.screens.settings.NotificationsScreen
import com.shortscap.app.screens.settings.PermissionDetailScreen
import com.shortscap.app.screens.settings.PermissionsScreen
import com.shortscap.app.screens.settings.SettingsSectionScreen
import com.shortscap.app.screens.settings.TextSizeScreen
import com.shortscap.app.screens.settings.ThemeScreen
import com.shortscap.app.viewmodel.AppUiState
import com.shortscap.app.viewmodel.AppViewModel

/** Route constants for every Settings destination hosted by [SettingsNavHost]. */
object SettingsDestinations {
    const val GENERAL = "settings_general"
    const val MONITORING = "settings_monitoring"
    const val PERMISSIONS = "settings_permissions"
    const val PERMISSION_DETAIL = "settings_permission_detail"
    const val NOTIFICATIONS = "settings_notifications"
    const val NOTIFICATION_CATEGORY = "settings_notification_category"
    const val APPEARANCE = "settings_appearance"
    const val APPEARANCE_THEME = "settings_appearance_theme"
    const val APPEARANCE_ICONS = "settings_appearance_icons"
    const val APPEARANCE_TEXT_SIZE = "settings_appearance_text_size"
    const val DATA_BACKUP = "settings_data_backup"
    const val LEGAL_DOCUMENT = "settings_legal_document"
    const val ABOUT = "settings_about"
    const val LANGUAGE = "settings_language"

    const val BLOCKED_APPS = "settings_blocked_apps"
    const val ALLOWED_APPS = "settings_allowed_apps"
    const val SCHEDULE = "settings_schedule"

    /** Route with the [PermissionId] name appended, e.g. settings_permission_detail/USAGE_ACCESS. */
    fun permissionDetailRoute(permissionId: PermissionId): String =
        "$PERMISSION_DETAIL/${permissionId.name}"

    /** Route with the [NotificationCategory] name appended, e.g. settings_notification_category/REMINDERS. */
    fun notificationCategoryRoute(category: NotificationCategory): String =
        "$NOTIFICATION_CATEGORY/${category.name}"

    /** Route with the legal document key appended, e.g. settings_legal_document/privacy. */
    fun legalDocumentRoute(document: String): String = "$LEGAL_DOCUMENT/$document"
}

private fun SettingsDestination.startRoute(): String = when (this) {
    SettingsDestination.GENERAL -> SettingsDestinations.GENERAL
    SettingsDestination.MONITORING -> SettingsDestinations.MONITORING
    SettingsDestination.PERMISSIONS -> SettingsDestinations.PERMISSIONS
    SettingsDestination.NOTIFICATIONS -> SettingsDestinations.NOTIFICATIONS
    SettingsDestination.APPEARANCE -> SettingsDestinations.APPEARANCE
    SettingsDestination.DATA_BACKUP -> SettingsDestinations.DATA_BACKUP
    SettingsDestination.ABOUT -> SettingsDestinations.ABOUT
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
    val strings = LocalAppStrings.current

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
            GeneralScreen(
                onOpenLanguage = { navController.navigate(SettingsDestinations.LANGUAGE) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.LANGUAGE) {
            LanguageScreen(
                currentLanguage = state.appLanguage,
                onApplyLanguage = viewModel::applyLanguage,
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
            PermissionsScreen(
                permissions = state.permissions,
                onRefreshPermissions = viewModel::refreshPermissions,
                onOpenDetail = { id -> navController.navigate(SettingsDestinations.permissionDetailRoute(id)) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(
            route = "${SettingsDestinations.PERMISSION_DETAIL}/{permissionId}",
            arguments = listOf(navArgument("permissionId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("permissionId")
                ?.let { name -> PermissionId.entries.firstOrNull { it.name == name } }
                ?: PermissionId.USAGE_ACCESS
            PermissionDetailScreen(
                permissionId = id,
                permission = state.permissions.firstOrNull { it.id == id }
                    ?: PermissionRepository.seedPermissions().first { it.id == id },
                onRefreshPermissions = viewModel::refreshPermissions,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.NOTIFICATIONS) {
            NotificationsScreen(
                onOpenCategory = { category ->
                    navController.navigate(SettingsDestinations.notificationCategoryRoute(category))
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(
            route = "${SettingsDestinations.NOTIFICATION_CATEGORY}/{category}",
            arguments = listOf(navArgument("category") { type = NavType.StringType }),
        ) { entry ->
            val category = entry.arguments?.getString("category")
                ?.let { name -> NotificationCategory.entries.firstOrNull { it.name == name } }
                ?: NotificationCategory.REMINDERS
            NotificationCategoryScreen(
                category = category,
                settings = state.notificationSettings.filter { it.id.category == category },
                onToggleSetting = viewModel::toggleNotificationSetting,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.APPEARANCE) {
            AppearanceScreen(
                onOpenTheme = { navController.navigate(SettingsDestinations.APPEARANCE_THEME) },
                onOpenIcons = { navController.navigate(SettingsDestinations.APPEARANCE_ICONS) },
                onOpenTextSize = { navController.navigate(SettingsDestinations.APPEARANCE_TEXT_SIZE) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.APPEARANCE_THEME) {
            ThemeScreen(
                themeMode = state.themeMode,
                onThemeModeChange = viewModel::setThemeMode,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.APPEARANCE_ICONS) {
            // Icon Style — selecting + Apply persists the style (via
            // IconRepository) and updates AppUiState.iconStyle, which is
            // provided app-wide through LocalIconStyle so the whole app
            // reflects the change instantly (no restart needed).
            IconScreen(
                currentStyle = state.iconStyle,
                onApply = viewModel::setIconStyle,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.APPEARANCE_TEXT_SIZE) {
            TextSizeScreen(
                textSizeMode = state.textSizeMode,
                onTextSizeChange = viewModel::setTextSizeMode,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.DATA_BACKUP) {
            SettingsSectionScreen(
                title = strings.dataBackupTitle,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.ABOUT) {
            AboutSettingsScreen(
                onOpenPrivacyPolicy = {
                    navController.navigate(SettingsDestinations.legalDocumentRoute("privacy"))
                },
                onOpenTermsConditions = {
                    navController.navigate(SettingsDestinations.legalDocumentRoute("terms"))
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // NOTE: Reset All Settings has no route or screen — it lives on the
        // Settings home as the last row and opens an in-place confirmation
        // dialog (ResetAllSettingsDialog) instead of navigating.

        composable(
            route = "${SettingsDestinations.LEGAL_DOCUMENT}/{document}",
            arguments = listOf(navArgument("document") { type = NavType.StringType }),
        ) { entry ->
            val document = when (entry.arguments?.getString("document")) {
                "terms" -> LegalDocument.TERMS_CONDITIONS
                else -> LegalDocument.PRIVACY_POLICY
            }
            LegalDocumentScreen(
                document = document,
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
