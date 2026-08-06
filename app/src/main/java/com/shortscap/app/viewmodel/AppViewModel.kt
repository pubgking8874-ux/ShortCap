package com.shortscap.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shortscap.app.theme.ThemeMode
import com.shortscap.app.theme.ThemePreferenceStore
import com.shortscap.app.model.DrawerScreen
import com.shortscap.app.model.MonitoringSettings
import com.shortscap.app.model.ProfileData
import com.shortscap.app.model.ScCircularMetric
import com.shortscap.app.model.ScScreen
import com.shortscap.app.model.SettingsDestination
import com.shortscap.app.model.SiteEntry
import com.shortscap.app.model.WebTab
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

    // WebScreen: active tab, search query, per-tab site lists
    val webTab: WebTab = WebTab.BLOCKED,
    val webQuery: String = "",
    val blockedSites: List<SiteEntry> = listOf(
        SiteEntry("Reddit", "reddit.com", true),
        SiteEntry("Twitter / X", "x.com", true),
        SiteEntry("TikTok Web", "tiktok.com", true),
    ),
    val allowedSites: List<SiteEntry> = listOf(
        SiteEntry("Google Docs", "docs.google.com", true),
        SiteEntry("Khan Academy", "khanacademy.org", true),
    ),
    val recentSites: List<SiteEntry> = listOf(
        SiteEntry("YouTube", "youtube.com", false),
        SiteEntry("Wikipedia", "wikipedia.org", false),
        SiteEntry("Coursera", "coursera.org", false),
    ),

    // Settings: dedicated sub-screen currently open (null = none). The
    // Monitoring settings live in [monitoring] — the single source of truth
    // for the Monitoring screen (backend GET/UPDATE APIs plug in later via a
    // repository seam without UI changes).
    val settingsDestination: SettingsDestination? = null,
    val monitoring: MonitoringSettings = MonitoringSettings(),
    val notificationsEnabled: Boolean = true,

    // Theme preference (persisted via ThemePreferenceStore)
    val themeMode: ThemeMode = ThemeMode.DARK,

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
    private val _uiState = MutableStateFlow(AppUiState(themeMode = themeStore.loadThemeMode()))
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
        showToast("Profile saved")
    }

    // Picked via the Android Photo Picker on the Profile screen. Future: Crop
    // step + upload through ProfileRepository.uploadProfilePicture.
    fun updateProfilePicture(uri: String) {
        _uiState.update { state ->
            state.copy(profile = state.profile.copy(pictureUri = uri))
        }
        showToast("Profile picture updated")
    }

    // ---- Theme (persists; applies instantly, no restart needed) ----
    fun setThemeMode(mode: ThemeMode) {
        themeStore.saveThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
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

    // ---- Web screen ----
    fun setWebTab(tab: WebTab) = _uiState.update { it.copy(webTab = tab) }
    fun setWebQuery(q: String) = _uiState.update { it.copy(webQuery = q) }

    fun toggleSite(tab: WebTab, name: String) {
        _uiState.update { state ->
            fun flip(list: List<SiteEntry>) = list.map { if (it.name == name) it.copy(on = !it.on) else it }
            when (tab) {
                WebTab.BLOCKED -> state.copy(blockedSites = flip(state.blockedSites))
                WebTab.ALLOWED -> state.copy(allowedSites = flip(state.allowedSites))
                WebTab.RECENT -> state.copy(recentSites = flip(state.recentSites))
            }
        }
        showToast(if (tab == WebTab.BLOCKED) "Website updated" else "Preference saved")
    }

    fun sitesFor(tab: WebTab): List<SiteEntry> = when (tab) {
        WebTab.BLOCKED -> uiState.value.blockedSites
        WebTab.ALLOWED -> uiState.value.allowedSites
        WebTab.RECENT -> uiState.value.recentSites
    }

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

    fun setNotifications(on: Boolean) = _uiState.update { it.copy(notificationsEnabled = on) }

    // Restores every setting to its default (theme included). Future: also
    // clear backend / cloud preferences through the SettingsRepository seam.
    fun resetAllSettings() {
        themeStore.saveThemeMode(ThemeMode.DARK)
        _uiState.update {
            it.copy(
                monitoring = MonitoringSettings(),
                notificationsEnabled = true,
                themeMode = ThemeMode.DARK,
            )
        }
        showToast("Settings reset")
    }
}
