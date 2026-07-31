package com.shortscap.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shortscap.app.model.ScScreen
import com.shortscap.app.screens.activity.ActivityScreen
import com.shortscap.app.screens.home.HomeScreen
import com.shortscap.app.screens.settings.SettingsScreen
import com.shortscap.app.screens.web.WebScreen
import com.shortscap.app.viewmodel.AppUiState
import com.shortscap.app.viewmodel.AppViewModel

/**
 * Mirrors the root conditional block:
 *   {screen === "home" && <HomeScreen loading={loading} />}
 *   {screen === "activity" && <ActivityScreen />}
 *   {screen === "web" && <WebScreen toast={showToast} />}
 *   {screen === "settings" && <SettingsScreen />}
 *
 * Simple `when` dispatch is used rather than Navigation-Compose's back-stack
 * navigation, since the RN app has no history/back-stack semantics between
 * these four tabs — each tap is a direct state swap, which this preserves
 * exactly. (Navigation Compose remains wired in for any deeper drill-down
 * screens added later, e.g. a website detail screen pushed from WebScreen.)
 */
@Composable
fun ScNavHost(state: AppUiState, viewModel: AppViewModel) {
    val scroll = rememberScrollState()
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
    ) {
        when (state.screen) {
            ScScreen.HOME -> HomeScreen(loading = state.homeLoading, metrics = state.homeMetrics)

            ScScreen.ACTIVITY -> ActivityScreen(
                range = state.activityRange,
                onRangeChange = viewModel::setActivityRange,
                expandedReport = state.expandedReport,
                onToggleReport = viewModel::toggleReport,
            )

            ScScreen.WEB -> WebScreen(
                tab = state.webTab,
                onTabChange = viewModel::setWebTab,
                query = state.webQuery,
                onQueryChange = viewModel::setWebQuery,
                sites = viewModel.sitesFor(state.webTab),
                onToggleSite = { name -> viewModel.toggleSite(state.webTab, name) },
                onAddWebsite = { viewModel.showToast("Add website") },
            )

            ScScreen.SETTINGS -> SettingsScreen(
                expandedCategory = state.expandedSettingsCategory,
                onToggleCategory = viewModel::toggleSettingsCategory,
                monitoringEnabled = state.monitoringEnabled,
                onMonitoringChange = viewModel::setMonitoring,
                notificationsEnabled = state.notificationsEnabled,
                onNotificationsChange = viewModel::setNotifications,
                themeMode = state.themeMode,
                onThemeModeChange = viewModel::setThemeMode,
                onResetAll = { viewModel.showToast("Settings reset") },
            )
        }
    }
}
