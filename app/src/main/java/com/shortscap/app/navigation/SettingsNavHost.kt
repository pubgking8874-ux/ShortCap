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
import com.shortscap.app.screens.settings.BreakReminderScreen
import com.shortscap.app.screens.settings.ChartScreen
import com.shortscap.app.screens.settings.FontScreen
import com.shortscap.app.screens.settings.GeneralScreen
import com.shortscap.app.screens.settings.IconScreen
import com.shortscap.app.screens.settings.LanguageScreen
import com.shortscap.app.screens.settings.MonitoringScheduleScreen
import com.shortscap.app.screens.settings.MonitoringScreen
import com.shortscap.app.screens.settings.NotificationCategoryScreen
import com.shortscap.app.screens.settings.NotificationsScreen
import com.shortscap.app.screens.settings.AddCustomSoundScreen
import com.shortscap.app.screens.settings.PermissionDetailScreen
import com.shortscap.app.screens.settings.PermissionsScreen
import com.shortscap.app.screens.settings.ShortsControlScreen
import com.shortscap.app.screens.settings.ShortsHudScreen
import com.shortscap.app.screens.settings.SoundConfigScreen
import com.shortscap.app.screens.settings.SoundEffectsScreen
import com.shortscap.app.screens.settings.StudyAllowedItemsScreen
import com.shortscap.app.screens.settings.StudyModeScreen
import com.shortscap.app.screens.settings.StudyScheduleEditScreen
import com.shortscap.app.screens.settings.StudyScheduleScreen
import com.shortscap.app.screens.settings.TextSizeScreen
import com.shortscap.app.screens.settings.ThemeScreen
import com.shortscap.app.sounds.SoundEffectCategory
import com.shortscap.app.study.FocusPasscodeEntry
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
    const val SOUND_EFFECTS = "settings_sound_effects"
    const val SOUND_CONFIG = "settings_sound_config"
    const val SOUND_ADD_CUSTOM = "settings_sound_add_custom"
    const val APPEARANCE = "settings_appearance"
    const val APPEARANCE_THEME = "settings_appearance_theme"
    const val APPEARANCE_ICONS = "settings_appearance_icons"
    const val APPEARANCE_CHART = "settings_appearance_chart"
    const val APPEARANCE_FONT = "settings_appearance_font"
    const val APPEARANCE_TEXT_SIZE = "settings_appearance_text_size"
    const val LEGAL_DOCUMENT = "settings_legal_document"
    const val ABOUT = "settings_about"
    const val LANGUAGE = "settings_language"

    const val BLOCKED_APPS = "settings_blocked_apps"
    const val ALLOWED_APPS = "settings_allowed_apps"
    const val SCHEDULE = "settings_schedule"
    const val SHORTS_CONTROL = "settings_shorts_control"
    const val SHORTS_HUD = "settings_shorts_hud"
    const val STUDY_MODE = "settings_study_mode"
    const val STUDY_BREAK_REMINDER = "settings_study_break_reminder"
    const val STUDY_ALLOWED = "settings_study_allowed"
    const val STUDY_SCHEDULE = "settings_study_schedule"
    const val STUDY_SCHEDULE_EDIT = "settings_study_schedule_edit"

    /** Route with the schedule id appended; pass "new" to create one. */
    fun studyScheduleEditRoute(id: String): String = "$STUDY_SCHEDULE_EDIT/$id"

    /** Route with the [PermissionId] name appended, e.g. settings_permission_detail/USAGE_ACCESS. */
    fun permissionDetailRoute(permissionId: PermissionId): String =
        "$PERMISSION_DETAIL/${permissionId.name}"

    /** Route with the [NotificationCategory] name appended, e.g. settings_notification_category/REMINDERS. */
    fun notificationCategoryRoute(category: NotificationCategory): String =
        "$NOTIFICATION_CATEGORY/${category.name}"

    /** Route with the [SoundEffectCategory] name appended, e.g. settings_sound_config/BREAK_REMINDER. */
    fun soundConfigRoute(category: SoundEffectCategory): String = "$SOUND_CONFIG/${category.name}"

    /** Route with the [SoundEffectCategory] name appended, e.g. settings_sound_add_custom/BREAK_REMINDER. */
    fun soundAddCustomRoute(category: SoundEffectCategory): String = "$SOUND_ADD_CUSTOM/${category.name}"

    /** Route with the legal document key appended, e.g. settings_legal_document/privacy. */
    fun legalDocumentRoute(document: String): String = "$LEGAL_DOCUMENT/$document"
}

private fun SettingsDestination.startRoute(): String = when (this) {
    SettingsDestination.GENERAL -> SettingsDestinations.GENERAL
    SettingsDestination.MONITORING -> SettingsDestinations.MONITORING
    SettingsDestination.PERMISSIONS -> SettingsDestinations.PERMISSIONS
    SettingsDestination.NOTIFICATIONS -> SettingsDestinations.NOTIFICATIONS
    SettingsDestination.SOUND_EFFECTS -> SettingsDestinations.SOUND_EFFECTS
    SettingsDestination.APPEARANCE -> SettingsDestinations.APPEARANCE
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

        composable(SettingsDestinations.STUDY_MODE) {
            // Study Mode — complete feature inside the Monitoring section
            // (relocated from General; UI, logic and data unchanged). State
            // flows from AppUiState (StudyModeSettings /
            // StudySession / StudySummary); the timestamp-based session keeps
            // the countdown exact across backgrounding, and a future backend
            // syncs through the StudyRepository seam behind the same shapes.
            StudyModeScreen(
                settings = state.studySettings,
                studyModeActive = state.studyModeActive,
                studyRemainingMillis = state.studyRemainingMillis,
                studyTotalMillis = state.studyTotalMillis,
                summary = state.studySummary,
                focusPasscodeSet = state.focusPasscodeSet,
                focusPasscodeSetAtMillis = state.focusPasscodeSetAtMillis,
                onStartSession = viewModel::startStudySession,
                onSetStudyDuration = viewModel::setStudyDuration,
                onOpenBreakReminder = { navController.navigate(SettingsDestinations.STUDY_BREAK_REMINDER) },
                onSetStudyBreakDuration = viewModel::setStudyBreakDuration,
                onSetStudySoundMode = viewModel::setStudySoundMode,
                onOpenSoundModeAccessSettings = viewModel::openSoundModeAccessSettings,
                // Study Schedule — a dedicated management screen with
                // multiple schedules (subject, days, start, duration,
                // reminder, enabled).
                onOpenStudySchedule = { navController.navigate(SettingsDestinations.STUDY_SCHEDULE) },
                onOpenAllowed = { navController.navigate(SettingsDestinations.STUDY_ALLOWED) },
                // Exit Passcode flows live in their own root overlay
                // (FocusPasscodeNavHost) — shared by Home AND Study Mode, so
                // both exit paths use the exact same verification/recovery UI.
                onOpenFocusPasscodeSetup = { viewModel.openFocusPasscodeFlow(FocusPasscodeEntry.SETUP) },
                onOpenFocusPasscodeVerify = { viewModel.openFocusPasscodeFlow(FocusPasscodeEntry.VERIFY) },
                // Card tap with a passcode set opens the STATUS screen (green
                // status + device date/time) — never a create/verify field.
                onOpenFocusPasscodeStatus = { viewModel.openFocusPasscodeFlow(FocusPasscodeEntry.STATUS) },
                // Three-dot (⋮) menu on the card deletes ONLY the Exit Passcode
                // configuration — the section returns to its Not Set state.
                onDeleteFocusPasscode = viewModel::deleteFocusPasscode,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // Break Reminder — the full configurable reminder system (enable /
        // interval / Once-Repeat / sound). Saving stores one
        // BreakReminderConfig into StudyModeSettings and returns to Study Mode.
        composable(SettingsDestinations.STUDY_BREAK_REMINDER) {
            BreakReminderScreen(
                config = state.studySettings.breakReminder,
                schedules = state.studySettings.schedules,
                onSave = { config ->
                    viewModel.setBreakReminderConfig(config)
                    navController.popBackStack()
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.STUDY_ALLOWED) {
            StudyAllowedItemsScreen(
                allowedApps = state.studySettings.allowedApps,
                allowedWebsites = state.studySettings.allowedWebsites,
                onToggleApp = viewModel::toggleStudyAllowedApp,
                onToggleWebsite = viewModel::toggleStudyAllowedWebsite,
                onAddWebsite = viewModel::addStudyAllowedWebsite,
                onAddApp = viewModel::addStudyAllowedApp,
                onRemoveApp = viewModel::removeStudyAllowedApp,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // Study Schedule — list of every scheduled session with per-schedule
        // toggle / Edit / Delete; "Add Schedule" opens the edit route with a
        // "new" id.
        composable(SettingsDestinations.STUDY_SCHEDULE) {
            StudyScheduleScreen(
                schedules = state.studySettings.schedules,
                onToggleEnabled = viewModel::toggleStudyScheduleEnabled,
                onEdit = { id -> navController.navigate(SettingsDestinations.studyScheduleEditRoute(id)) },
                onAdd = { navController.navigate(SettingsDestinations.studyScheduleEditRoute("new")) },
                onDelete = viewModel::deleteStudySchedule,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // Add ("new") / Edit (existing id) — a schedule. Saving creates or
        // updates ONLY that schedule and returns to the list.
        composable(
            route = "${SettingsDestinations.STUDY_SCHEDULE_EDIT}/{scheduleId}",
            arguments = listOf(navArgument("scheduleId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("scheduleId") ?: "new"
            val existing = if (id == "new") null else state.studySettings.schedules.firstOrNull { it.id == id }
            StudyScheduleEditScreen(
                existing = existing,
                onSave = { subject, days, start, duration, reminder ->
                    if (existing == null) {
                        viewModel.addStudySchedule(subject, days, start, duration, reminder)
                    } else {
                        viewModel.updateStudySchedule(existing.id, subject, days, start, duration, reminder)
                    }
                    navController.popBackStack()
                },
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
                // Same derived permission-based paused state the Home section
                // uses — Device Monitoring can never disagree with it.
                monitoringPaused = state.monitoringPaused,
                onToggleMonitoring = viewModel::setMonitoringEnabled,
                onToggleStrictMode = viewModel::setStrictMode,
                // Study Mode — the complete feature (relocated from General),
                // opening the SAME StudyModeScreen and flows as before.
                onOpenStudyMode = { navController.navigate(SettingsDestinations.STUDY_MODE) },
                onOpenShortsControl = { navController.navigate(SettingsDestinations.SHORTS_CONTROL) },
                onOpenSchedule = { navController.navigate(SettingsDestinations.SCHEDULE) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.SHORTS_CONTROL) {
            // Shorts Control — per-platform Shorts monitoring switches. State
            // comes from the same MonitoringSettings.platforms the backend
            // GET/UPDATE APIs will feed; the screen never hardcodes values.
            ShortsControlScreen(
                platforms = state.monitoring.platforms,
                onTogglePlatform = viewModel::togglePlatform,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.SHORTS_HUD) {
            // Shorts HUD — floating counter overlay appearance settings,
            // opened from Settings → Appearance → Shorts HUD. All state lives
            // in the local ShortsHudSettingsStore; the selected appearance is
            // pushed live to the running controller so a visible overlay
            // switches mode immediately.
            ShortsHudScreen(
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

        // ---- Sound & Effects — ONE control-center screen. ----
        // Fully separate from the Android device Sound / Vibrate / Silent
        // mode. All nine sound options live on the single Sound & Effects
        // page under three section headings; every row opens the SAME
        // configuration screen (no duplicate sound settings, no nested
        // category pages).
        composable(SettingsDestinations.SOUND_EFFECTS) {
            SoundEffectsScreen(
                onOpenSound = { category ->
                    navController.navigate(SettingsDestinations.soundConfigRoute(category))
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // Individual sound configuration — shared by every sound option.
        composable(
            route = "${SettingsDestinations.SOUND_CONFIG}/{category}",
            arguments = listOf(navArgument("category") { type = NavType.StringType }),
        ) { entry ->
            val category = entry.arguments?.getString("category")
                ?.let { name -> SoundEffectCategory.entries.firstOrNull { it.name == name } }
                ?: SoundEffectCategory.BREAK_REMINDER
            SoundConfigScreen(
                category = category,
                loadSounds = viewModel::loadLocalSounds,
                selectedSoundId = viewModel::localSelectedSound,
                onSelectSound = viewModel::setLocalSelectedSound,
                onAddFromDevice = {
                    navController.navigate(SettingsDestinations.soundAddCustomRoute(category))
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // Add from Device — placeholder destination for the future media /
        // file picker flow (UI/navigation only today).
        composable(
            route = "${SettingsDestinations.SOUND_ADD_CUSTOM}/{category}",
            arguments = listOf(navArgument("category") { type = NavType.StringType }),
        ) { entry ->
            val category = entry.arguments?.getString("category")
                ?.let { name -> SoundEffectCategory.entries.firstOrNull { it.name == name } }
                ?: SoundEffectCategory.BREAK_REMINDER
            AddCustomSoundScreen(
                category = category,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.APPEARANCE) {
            AppearanceScreen(
                chartStyle = state.chartStyle,
                fontMode = state.fontMode,
                onOpenTheme = { navController.navigate(SettingsDestinations.APPEARANCE_THEME) },
                onOpenIcons = { navController.navigate(SettingsDestinations.APPEARANCE_ICONS) },
                onOpenChart = { navController.navigate(SettingsDestinations.APPEARANCE_CHART) },
                onOpenFont = { navController.navigate(SettingsDestinations.APPEARANCE_FONT) },
                onOpenTextSize = { navController.navigate(SettingsDestinations.APPEARANCE_TEXT_SIZE) },
                onOpenShortsHud = { navController.navigate(SettingsDestinations.SHORTS_HUD) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        composable(SettingsDestinations.APPEARANCE_FONT) {
            // Font — tapping a family applies + persists it immediately (no
            // Apply/Save step): the centralized typography system re-renders
            // the whole application via ScFonts, so every screen updates.
            FontScreen(
                currentFont = state.fontMode,
                onSelect = viewModel::setFontMode,
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

        composable(SettingsDestinations.APPEARANCE_CHART) {
            // Chart Style — selecting + Apply persists the global preference
            // (via AppearanceRepository) and updates AppUiState.chartStyle,
            // which Activity, Web Analytics and every future chart read so
            // the same usage data simply re-renders in the chosen style.
            ChartScreen(
                currentStyle = state.chartStyle,
                onApply = viewModel::setChartStyle,
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
