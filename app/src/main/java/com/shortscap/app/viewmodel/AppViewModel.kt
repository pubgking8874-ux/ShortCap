package com.shortscap.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shortscap.app.theme.ThemeMode
import com.shortscap.app.theme.ThemePreferenceStore
import com.shortscap.app.model.ScCircularMetric
import com.shortscap.app.model.ScScreen
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
    // Root: useState("home"), drawerOpen, profileOpen, loading, toastMsg
    val screen: ScScreen = ScScreen.HOME,
    val drawerOpen: Boolean = false,
    val profileOpen: Boolean = false,
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

    // SettingsScreen: expanded category + toggles
    val expandedSettingsCategory: String? = null,
    val monitoringEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,

    // Theme preference (persisted via ThemePreferenceStore)
    val themeMode: ThemeMode = ThemeMode.DARK,
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
    fun toggleProfileMenu() = _uiState.update { it.copy(profileOpen = !it.profileOpen) }
    fun closeProfileMenu() = _uiState.update { it.copy(profileOpen = false) }

    // ---- Theme (persists; applies instantly, no restart needed) ----
    fun setThemeMode(mode: ThemeMode) {
        themeStore.saveThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
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

    // ---- Settings screen ----
    fun toggleSettingsCategory(key: String) = _uiState.update {
        it.copy(expandedSettingsCategory = if (it.expandedSettingsCategory == key) null else key)
    }
    fun setMonitoring(on: Boolean) = _uiState.update { it.copy(monitoringEnabled = on) }
    fun setNotifications(on: Boolean) = _uiState.update { it.copy(notificationsEnabled = on) }
}
