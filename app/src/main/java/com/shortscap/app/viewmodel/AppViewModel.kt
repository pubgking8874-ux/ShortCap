package com.shortscap.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shortscap.app.appearance.AppearanceRepository
import com.shortscap.app.appearance.TextSizeMode
import com.shortscap.app.theme.ThemeMode
import com.shortscap.app.theme.ThemePreferenceStore
import com.shortscap.app.i18n.AppLanguage
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LanguagePreferenceStore
import com.shortscap.app.icons.IconRepository
import com.shortscap.app.icons.IconStyle
import com.shortscap.app.model.DrawerScreen
import com.shortscap.app.model.MonitoringSettings
import com.shortscap.app.model.ProfileData
import com.shortscap.app.notifications.NotificationRepository
import com.shortscap.app.notifications.NotificationSetting
import com.shortscap.app.notifications.NotificationSettingId
import com.shortscap.app.permissions.PermissionInfo
import com.shortscap.app.permissions.PermissionRepository
import com.shortscap.app.settings.SettingsManager
import com.shortscap.app.model.ScCircularMetric
import com.shortscap.app.model.ScScreen
import com.shortscap.app.model.SettingsDestination
import com.shortscap.app.web.WebAnalyticsPeriod
import com.shortscap.app.web.WebRepository
import com.shortscap.app.web.WebRule
import com.shortscap.app.web.WebRuleStatus
import com.shortscap.app.web.WebUsageRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth for app-wide UI state.
 * Each field below corresponds 1:1 to a `useState` call in the RN root
 * (`ShortsCapApp`) or one of its screens, per the "state management" and
 * "MVVM Architecture / StateFlow / ViewModel" requirements of the migration.
 */
data class AppUiState(
    // Root: useState("home"), drawerOpen, profileScreenOpen, loading, toastMsg
    val screen: ScScreen = ScScreen.HOME,
    val drawerOpen: Boolean = false,
    val profileScreenOpen: Boolean = false,
    val homeLoading: Boolean = true,
    val toastMessage: String? = null,

    // Home hero circular analytics — mocked here for the frontend; the
    // ViewModel will replace these with backend API responses later
    // (only the data source changes, the UI stays the same).
    val homeMetrics: List<ScCircularMetric> = listOf(
        ScCircularMetric(id = "shorts-watch-time", label = "Today's Shorts Watch Time", value = "1h 30m", progress = 0.75f),
        ScCircularMetric(id = "shorts-watched", label = "Today's Shorts Watched", value = "245", unit = "Shorts", progress = 0.65f),
    ),

    // ActivityScreen: range chip + expanded report card
    val activityRange: String = "Weekly",
    val expandedReport: String? = null,

    // Web section: analytics period + website rules + raw usage records. All
    // data flows from WebRepository (seeds today; backend/database later) —
    // never hardcoded in the UI. Analytics summaries are derived from
    // [webUsageRecords] via WebRepository.analyticsSummary().
    val webPeriod: WebAnalyticsPeriod = WebAnalyticsPeriod.TODAY,
    val webRules: List<WebRule> = WebRepository.seedRules(),
    val webUsageRecords: List<WebUsageRecord> = WebRepository.seedUsageRecords(),

    // Settings: dedicated sub-screen currently open (null = none). The
    // Monitoring settings live in [monitoring] — the single source of truth
    // for the Monitoring screen (backend GET/UPDATE APIs plug in later via a
    // repository seam without UI changes).
    val settingsDestination: SettingsDestination? = null,
    val monitoring: MonitoringSettings = MonitoringSettings(),

    // Notifications module — every option's on/off state, persisted locally
    // by [NotificationRepository] (backend-ready: same shape maps 1:1 to a
    // future GET/POST /notifications/settings API).
    val notificationSettings: List<NotificationSetting> = NotificationRepository.seedSettings(),

    // Permissions management center — live statuses resolved from the Android
    // OS by [PermissionRepository]; refreshed automatically whenever a
    // Permissions screen resumes (e.g. after returning from Android Settings).
    val permissions: List<PermissionInfo> = PermissionRepository.seedPermissions(),

    // Theme preference (persisted via ThemePreferenceStore)
    val themeMode: ThemeMode = ThemeMode.DARK,

    // Appearance preference — global text scale, applied app-wide via a root
    // LocalDensity fontScale override (only typography changes; layouts,
    // icons, cards and spacing stay untouched). Persisted locally by
    // AppearanceRepository (backend-ready: maps 1:1 to a future
    // GET/POST /settings/appearance API).
    val textSizeMode: TextSizeMode = TextSizeMode.MEDIUM,

    // Icon Style preference — which icon system renders app-wide (ShortsCap
    // Original blue/black, or the Vibrant colorful category system). Held in
    // [LocalIconStyle] at the app root so every screen updates instantly on
    // Apply. Persisted locally by IconRepository (backend-ready: maps 1:1 to
    // a future user_id / selected_icon_style / updated_at backend entry).
    val iconStyle: IconStyle = IconStyle.ORIGINAL,

    // Language preference (persisted via LanguagePreferenceStore and applied
    // to the whole logged-in experience through LocalAppStrings). The Auth
    // flow always stays English and does not read this.
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,

    // True while a language change is being applied — ShortsCapApp shows a
    // smooth transition overlay instead of an abrupt screen reload.
    val languageApplying: Boolean = false,

    // Full-screen drawer sub-screen currently open (null = none). Modular:
    // each destination maps to a dedicated UI screen; backend APIs plug in
    // later without redesigning any screen.
    val drawerScreen: DrawerScreen? = null,

    // Local profile shown/edited on the Profile screen. Load / Update / Upload
    // Picture will come from backend APIs (ProfileRepository seam) later.
    val profile: ProfileData = ProfileData(),

    // Session placeholder — false shows the Auth flow (Splash -> Welcome ->
    // Login/CreateAccount/Guest) on launch. When AWS Cognito / the Python
    // backend / JWT are connected, set this from the session state so the
    // app opens straight to the Dashboard; no UI changes are required.
    val sessionActive: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val themeStore = ThemePreferenceStore(application)
    private val languageStore = LanguagePreferenceStore(application)
    private val _uiState = MutableStateFlow(
        AppUiState(
            themeMode = themeStore.loadThemeMode(),
            appLanguage = languageStore.loadLanguage(),
            notificationSettings = NotificationRepository.loadSettings(application),
            textSizeMode = AppearanceRepository.loadTextSizeMode(application),
            iconStyle = IconRepository.loadIconStyle(application),
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        // Mirrors: useEffect(() => { setTimeout(() => setLoading(false), 900) }, [])
        viewModelScope.launch {
            delay(900)
            _uiState.update { it.copy(homeLoading = false) }
        }
    }

    // ---- Navigation / chrome ----
    fun setScreen(screen: ScScreen) = _uiState.update { it.copy(screen = screen) }
    fun openDrawer() = _uiState.update { it.copy(drawerOpen = true) }
    fun closeDrawer() = _uiState.update { it.copy(drawerOpen = false) }

    // ---- Profile screen (opened from the Dashboard top bar) ----
    fun openProfileScreen() = _uiState.update { it.copy(profileScreenOpen = true) }
    fun closeProfileScreen() = _uiState.update { it.copy(profileScreenOpen = false) }

    // Localized toast — resolves the message through the active language's
    // catalog so every toast follows the selected language.
    fun showToast(message: (AppStrings) -> String) {
        showToast(message(AppStrings.forLanguage(uiState.value.appLanguage)))
    }

    // ---- Profile (local-only today; backend seam: ProfileRepository) ----
    fun saveProfile(fullName: String, gender: String?, dateOfBirth: String?) {
        _uiState.update { state ->
            state.copy(
                profile = state.profile.copy(
                    fullName = fullName.trim(),
                    gender = gender,
                    dateOfBirth = dateOfBirth,
                ),
            )
        }
        showToast { it.toastProfileSaved }
    }

    // Picked via the Android Photo Picker on the Profile screen. Future: Crop
    // step + upload through ProfileRepository.uploadProfilePicture.
    fun updateProfilePicture(uri: String) {
        _uiState.update { state ->
            state.copy(profile = state.profile.copy(pictureUri = uri))
        }
        showToast { it.toastProfilePictureUpdated }
    }

    // ---- Theme (persists; applies instantly, no restart needed) ----
    fun setThemeMode(mode: ThemeMode) {
        themeStore.saveThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    // ---- Appearance (persists locally; future: AppearanceRepository cloud
    //      sync / analytics seams) ----
    fun setTextSizeMode(mode: TextSizeMode) {
        AppearanceRepository.saveTextSizeMode(getApplication(), mode)
        _uiState.update { it.copy(textSizeMode = mode) }
        // Future backend: AppearanceRepository.syncAppearanceToCloud(mode).
    }

    // ---- Icon Style (persists locally; updates the global icon provider
    //      instantly — the whole app reflects the new style via LocalIconStyle) ----
    fun setIconStyle(style: IconStyle) {
        if (style == uiState.value.iconStyle) return
        IconRepository.saveIconStyle(getApplication(), style)
        _uiState.update { it.copy(iconStyle = style) }
        // Future backend: IconRepository.syncIconStyleToCloud(style);
        // Future analytics: IconRepository.trackIconStyleAnalytics(style).
    }

    // ---- Session (mock seam for the auth flow) ----
    // Called by the auth graph's onExitToDashboard (Continue as Guest / mock
    // Sign In / mock Create Account) to enter the Dashboard. Backend login
    // will replace this with real session state — the UI stays the same.
    fun setSessionActive(active: Boolean) = _uiState.update { it.copy(sessionActive = active) }

    // ---- Drawer sub-screens (modular; backend-ready) ----
    fun openDrawerScreen(screen: DrawerScreen) =
        _uiState.update { it.copy(drawerOpen = false, drawerScreen = screen) }

    fun closeDrawerScreen() = _uiState.update { it.copy(drawerScreen = null) }

    // ---- Language (persists locally; future: LanguageRepository cloud sync) ----
    private var languageJob: Job? = null

    // Applies a new language across the entire logged-in experience. The
    // language is persisted and swapped immediately; a brief applying overlay
    // covers the transition so the UI refresh feels smooth, not abrupt.
    fun applyLanguage(language: AppLanguage) {
        if (language == uiState.value.appLanguage) return
        languageStore.saveLanguage(language)
        _uiState.update { it.copy(appLanguage = language, languageApplying = true) }
        languageJob?.cancel()
        languageJob = viewModelScope.launch {
            delay(650)
            _uiState.update { it.copy(languageApplying = false) }
        }
        // Future backend: LanguageRepository.syncLanguageToCloud(language)
    }

    // ---- Toast (mirrors showToast + clearTimeout/setTimeout dance) ----
    private var toastJob: Job? = null
    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2200)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    // ---- Activity screen ----
    fun setActivityRange(range: String) = _uiState.update { it.copy(activityRange = range) }
    fun toggleReport(report: String) = _uiState.update {
        it.copy(expandedReport = if (it.expandedReport == report) null else report)
    }

    // ---- Web section (rules + analytics; backend-ready via WebRepository) ----
    fun setWebPeriod(period: WebAnalyticsPeriod) =
        _uiState.update { it.copy(webPeriod = period) }

    /**
     * Adds a website rule (display name derived from the domain). Returns
     * false when the domain is invalid or a rule already exists — the UI
     * validates first, this is a defensive guard.
     */
    fun addWebRule(domain: String, status: WebRuleStatus): Boolean {
        val d = domain.trim()
        if (d.isBlank() || !d.contains(".")) return false
        if (uiState.value.webRules.any { it.domain.equals(d, ignoreCase = true) }) return false
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                webRules = state.webRules + WebRule(
                    id = d.lowercase(),
                    domain = d,
                    displayName = deriveWebDisplayName(d),
                    status = status,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        showToast { if (status == WebRuleStatus.BLOCKED) it.webToastBlocked else it.webToastAllowed }
        return true
    }

    /**
     * Primary blocking action (main Web screen): blocks [domain], creating a
     * BLOCKED rule when it is new, or flipping an existing ALLOWED rule to
     * BLOCKED. Returns false only for invalid input or when it is already
     * blocked (the UI shows the reason inline).
     */
    fun blockWebsite(domain: String): Boolean {
        val d = domain.trim()
        if (d.isBlank() || !d.contains(".")) return false
        val existing = uiState.value.webRules.firstOrNull { it.domain.equals(d, ignoreCase = true) }
        if (existing != null) {
            if (existing.status == WebRuleStatus.BLOCKED) return false
            setWebRuleStatus(existing.domain, WebRuleStatus.BLOCKED)
            return true
        }
        return addWebRule(d, WebRuleStatus.BLOCKED)
    }

    /** Moves a website between the blocked and allowed lists. */
    fun setWebRuleStatus(domain: String, status: WebRuleStatus) {
        _uiState.update { state ->
            state.copy(
                webRules = state.webRules.map {
                    if (it.domain.equals(domain, ignoreCase = true)) {
                        it.copy(status = status, updatedAt = System.currentTimeMillis())
                    } else it
                },
            )
        }
        showToast { if (status == WebRuleStatus.BLOCKED) it.webToastBlocked else it.webToastUnblocked }
    }

    /** Removes a website rule from the list. */
    fun removeWebRule(domain: String) {
        _uiState.update { state ->
            state.copy(webRules = state.webRules.filterNot { it.domain.equals(domain, ignoreCase = true) })
        }
        showToast { it.webToastRemoved }
    }

    /** "example.com" -> "Example" — display name for manually added websites. */
    private fun deriveWebDisplayName(domain: String): String =
        domain.removePrefix("www.").substringBefore(".").replaceFirstChar { it.uppercase() }

    // ---- Settings sub-screens (dedicated screens; Navigation Compose back
    //      stack hosted in SettingsNavHost; backend-ready) ----
    fun openSettingsScreen(destination: SettingsDestination) =
        _uiState.update { it.copy(settingsDestination = destination) }

    fun closeSettingsScreen() = _uiState.update { it.copy(settingsDestination = null) }

    // ---- Monitoring settings (local today; GET/UPDATE Monitoring Settings
    //      backend APIs + SettingsRepository seam later) ----
    fun setMonitoringEnabled(on: Boolean) =
        _uiState.update { it.copy(monitoring = it.monitoring.copy(enabled = on)) }

    fun setAppBlockingEnabled(on: Boolean) =
        _uiState.update { it.copy(monitoring = it.monitoring.copy(appBlockingEnabled = on)) }

    fun setScreenTimeLimit(minutes: Int) =
        _uiState.update { it.copy(monitoring = it.monitoring.copy(screenTimeLimitMinutes = minutes)) }

    fun setStrictMode(on: Boolean) =
        _uiState.update { it.copy(monitoring = it.monitoring.copy(strictModeEnabled = on)) }

    fun togglePlatform(id: String) = _uiState.update { state ->
        state.copy(
            monitoring = state.monitoring.copy(
                platforms = state.monitoring.platforms.map {
                    if (it.id == id) it.copy(enabled = !it.enabled) else it
                },
            ),
        )
    }

    fun setBreakReminderEnabled(on: Boolean) =
        _uiState.update { it.copy(monitoring = it.monitoring.copy(breakReminderEnabled = on)) }

    fun setBreakReminderInterval(minutes: Int) =
        _uiState.update { it.copy(monitoring = it.monitoring.copy(breakReminderIntervalMinutes = minutes)) }

    // ---- Notifications (local persistence today; future: NotificationRepository
    //      cloud sync / analytics seams) ----
    fun toggleNotificationSetting(id: NotificationSettingId, enabled: Boolean) {
        val updated = uiState.value.notificationSettings.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        NotificationRepository.saveSettings(getApplication(), updated)
        _uiState.update { it.copy(notificationSettings = updated) }
        // Future backend: NotificationRepository.syncSettingsToCloud(updated);
        // Future analytics: NotificationRepository.trackNotificationAnalytics(id, enabled).
    }

    // ---- Permissions (live OS checks; backend-ready via PermissionRepository)
    //      Called on every Permissions-screen resume so statuses stay fresh
    //      after the user returns from Android Settings — no manual refresh. ----
    fun refreshPermissions() {
        _uiState.update { state ->
            state.copy(permissions = PermissionRepository.checkAll(getApplication(), state.permissions))
        }
    }

    // Restores every resettable application setting to its default through the
    // centralized SettingsManager (Theme → System Default, Text Size → Medium,
    // Language → English, Monitoring + Notification preferences → defaults).
    // Account, profile, session, tokens and history are never touched.
    // Future: SettingsManager.resetCloudSettings() clears backend/cloud too.
    fun resetAllSettings() {
        SettingsManager.restoreDefaults(getApplication())
        _uiState.update {
            it.copy(
                monitoring = SettingsManager.defaultMonitoring(),
                notificationSettings = SettingsManager.defaultNotificationSettings(),
                themeMode = SettingsManager.defaultThemeMode(),
                textSizeMode = SettingsManager.defaultTextSizeMode(),
                iconStyle = SettingsManager.defaultIconStyle(),
                appLanguage = SettingsManager.defaultLanguage(),
            )
        }
        showToast { it.toastSettingsReset }
    }
}
