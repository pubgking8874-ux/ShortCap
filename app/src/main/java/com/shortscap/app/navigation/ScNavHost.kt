package com.shortscap.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.activity.ActivityPeriod
import com.shortscap.app.model.ScScreen
import com.shortscap.app.screens.activity.ActivityReportScreen
import com.shortscap.app.screens.activity.ActivityScreen
import com.shortscap.app.screens.home.HomeScreen
import com.shortscap.app.screens.settings.SettingsScreen
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .navigationBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        when (state.screen) {
            ScScreen.HOME -> HomeScreen(
                loading = state.homeLoading,
                metrics = state.homeMetrics,
                appsUsedToday = state.homeAppsUsedToday,
                blockedWebCount = state.blockedWebCount,
                allowedWebCount = state.allowedWebCount,
                // Centralized monitoring-paused state (derived from the live
                // permission list) — injects the Monitoring Paused section as
                // the first swipe page and auto-clears once permissions return.
                monitoringPaused = state.monitoringPaused,
                missingRequiredPermissions = state.missingRequiredMonitoringPermissions,
                // Tapping the paused circle re-checks the required permissions
                // before showing the resume popup.
                onRefreshPermissions = viewModel::refreshPermissions,
                // Localized fallback when no Android settings screen could be
                // opened for a missing permission (e.g. Accessibility Settings).
                onPermissionSettingsUnavailable = {
                    viewModel.showToast { it.permissionSettingsUnavailableToast }
                },
                onOpenActivityDaily = viewModel::openActivityDaily,
                onOpenWebAllowed = { viewModel.openWebSection(WebDestinations.ALLOWED) },
                onOpenWebBlocked = { viewModel.openWebSection(WebDestinations.BLOCKED) },
            )

            ScScreen.ACTIVITY -> {
                // Dedicated report / range-detail screens (full pages inside
                // the Activity tab): a tapped monthly date-range bar opens its
                // per-day detail, the Reports section opens Weekly/Monthly
                // reports; the system Back button returns to the Activity page.
                val reportPeriod = state.activityReport
                val rangeDetail = state.activityRangeDetail
                when {
                    rangeDetail != null -> ActivityReportScreen(
                        period = ActivityPeriod.MONTHLY,
                        range = rangeDetail,
                        chartStyle = state.chartStyle,
                        onBack = viewModel::closeActivityRangeDetail,
                    )
                    reportPeriod != null -> ActivityReportScreen(
                        period = reportPeriod,
                        chartStyle = state.chartStyle,
                        onBack = viewModel::closeActivityReport,
                        onOpenRange = viewModel::openActivityRangeDetail,
                    )
                    else -> ActivityScreen(
                        range = state.activityRange,
                        onRangeChange = viewModel::setActivityRange,
                        chartStyle = state.chartStyle,
                        onOpenReport = viewModel::openActivityReport,
                        onOpenRange = viewModel::openActivityRangeDetail,
                    )
                }
            }

            // The Web tab is a dedicated nav stack: analytics root + Blocked /
            // Allowed rule screens (WebNavHost), all data via WebRepository.
            ScScreen.WEB -> WebNavHost(state = state, viewModel = viewModel)

            ScScreen.SETTINGS -> SettingsScreen(
                onOpenDestination = viewModel::openSettingsScreen,
                onResetAll = viewModel::resetAllSettings,
            )
        }
    }

    // System Back while an Activity report / range-detail screen is open
    // returns to the Activity page (never exits the app). Composed after the
    // content so it takes precedence over the default behavior.
    BackHandler(
        enabled = state.screen == ScScreen.ACTIVITY &&
            (state.activityReport != null || state.activityRangeDetail != null),
    ) {
        if (state.activityRangeDetail != null) viewModel.closeActivityRangeDetail()
        else viewModel.closeActivityReport()
    }
}
