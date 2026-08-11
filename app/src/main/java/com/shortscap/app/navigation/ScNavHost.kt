package com.shortscap.app.navigation

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shortscap.app.activity.ActivityPeriod
import com.shortscap.app.model.ScScreen
import com.shortscap.app.screens.activity.ActivityReportScreen
import com.shortscap.app.screens.activity.ActivityScreen
import com.shortscap.app.screens.home.HomeScreen
import com.shortscap.app.screens.settings.SettingsScreen
import com.shortscap.app.study.FocusPasscodeEntry
import com.shortscap.app.viewmodel.AppUiState
import com.shortscap.app.viewmodel.AppViewModel

/** Double-back-to-exit window — a second Back press within this many ms exits. */
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

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
    // ---- Double-back-to-exit at any tab root (no child screen open). ----
    // The four bottom tabs are direct state swaps with no back stack, so when
    // no sub-screen / overlay is open a Back press has nothing to navigate
    // back to. First press shows "Press back again to exit"; a second press
    // within [EXIT_CONFIRM_WINDOW_MS] finishes the activity. The pending
    // first press is a plain timestamp (auto-resets after the window) and is
    // also cleared on ANY navigation (tab switch, or entering/leaving a child
    // screen), so a stale press can never exit the app from a different
    // context.
    //
    // This handler is composed BEFORE the tab content and the overlay
    // BackHandlers (Settings / Drawer / Profile / Passcode), so those
    // child-screen handlers always take precedence (Compose dispatches back
    // callbacks in reverse registration order) — double-back-to-exit fires
    // only when the user is genuinely at a tab root with nothing else open.
    val atTabRoot = state.settingsDestination == null &&
        state.drawerScreen == null &&
        !state.drawerOpen &&
        !state.profileScreenOpen &&
        state.focusPasscodeFlow == null &&
        !(state.screen == ScScreen.ACTIVITY &&
            (state.activityReport != null || state.activityRangeDetail != null))
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = atTabRoot) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt <= EXIT_CONFIRM_WINDOW_MS) {
            (context as? Activity)?.finish()
        } else {
            lastBackAt = now
            viewModel.showToast { it.exitConfirmToast }
        }
    }
    // Reset the pending first press whenever the root context changes
    // (tab switch, or entering/leaving a child screen / overlay).
    LaunchedEffect(atTabRoot, state.screen) {
        lastBackAt = 0L
    }
    // Also reset whenever the app returns to the foreground (e.g. after the
    // user comes back from an Android system settings page opened from the
    // Permissions flow) — returning from a system screen can never count as
    // the "second press".
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) lastBackAt = 0L
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                // Today's total usage — same ActivityRepository Daily data the
                // Activity → Daily chart uses (Home stays in sync with it).
                todayUsageMinutes = state.homeTodayUsageMinutes,
                appsUsedToday = state.homeAppsUsedToday,
                blockedWebCount = state.blockedWebCount,
                allowedWebCount = state.allowedWebCount,
                // Centralized monitoring-paused state (derived from the live
                // permission list) — injects the Monitoring Paused section as
                // the first swipe page and auto-clears once permissions return.
                monitoringPaused = state.monitoringPaused,
                missingRequiredPermissions = state.missingRequiredMonitoringPermissions,
                // Study Mode — timestamp-based remaining time; the Home
                // carousel leads with the study countdown while active and
                // returns to the normal Shorts monitoring UI at 00:00. Tapping
                // the active Study Mode page opens the SHARED Exit Passcode
                // verification (same screen as Study Mode).
                studyModeActive = state.studyModeActive,
                studyRemainingMillis = state.studyRemainingMillis,
                studyTotalMillis = state.studyTotalMillis,
                onStopStudyMode = {
                    viewModel.openFocusPasscodeFlow(
                        if (state.focusPasscodeSet) FocusPasscodeEntry.VERIFY else FocusPasscodeEntry.SETUP,
                    )
                },
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
