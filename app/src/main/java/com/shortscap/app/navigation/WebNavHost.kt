package com.shortscap.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shortscap.app.screens.web.WebAllowedScreen
import com.shortscap.app.screens.web.WebAnalyticsScreen
import com.shortscap.app.screens.web.WebBlockedScreen
import com.shortscap.app.screens.web.WebBlockingScreen
import com.shortscap.app.screens.web.WebRecentScreen
import com.shortscap.app.viewmodel.AppUiState
import com.shortscap.app.viewmodel.AppViewModel
import com.shortscap.app.web.WebAnalyticsPeriod
import com.shortscap.app.web.WebRepository
import com.shortscap.app.web.WebRuleStatus

/**
 * Route constants for the Web tab. The MAIN screen is the Website Blocking
 * hub ([WebDestinations.BLOCKING]); every other screen is a dedicated
 * secondary page: Blocked, Allowed, Recent, and — opened ONLY from the
 * Web Time card — Web Usage Analytics.
 */
object WebDestinations {
    const val BLOCKING = "web_blocking"
    const val BLOCKED = "web_blocked"
    const val ALLOWED = "web_allowed"
    const val RECENT = "web_recent"
    const val ANALYTICS = "web_analytics"
}

/**
 * Web tab navigation.
 *
 * Website blocking remains the primary feature: the Web tab opens the
 * Website Blocking screen; Blocked / Allowed / Recent open dedicated rule
 * screens; the Web Time card on the blocking screen opens the Web Usage
 * Analytics screen. System Back pops every secondary page back to the
 * blocking root, which then behaves like every other bottom tab.
 *
 * All data comes from [AppUiState] via the ViewModel — rules, analytics
 * summaries and raw usage records flow through WebRepository, never through
 * hardcoded UI data.
 */
@Composable
fun WebNavHost(state: AppUiState, viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Pure aggregations — cached for the current records/period so they are
    // not re-derived on every recomposition (data source stays the ViewModel).
    val summary = remember(state.webUsageRecords, state.webPeriod) {
        WebRepository.analyticsSummary(state.webUsageRecords, state.webPeriod)
    }
    val todayTotal = remember(state.webUsageRecords) {
        WebRepository.analyticsSummary(state.webUsageRecords, WebAnalyticsPeriod.TODAY).totalMinutes
    }
    val existingDomains = remember(state.webRules) { state.webRules.map { it.domain }.toSet() }

    NavHost(
        navController = navController,
        startDestination = WebDestinations.BLOCKING,
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 8 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 8 } },
    ) {
        // ---- Main Web screen — Website Blocking (primary) ----
        composable(WebDestinations.BLOCKING) {
            WebBlockingScreen(
                blockedCount = state.webRules.count { it.status == WebRuleStatus.BLOCKED },
                allowedCount = state.webRules.count { it.status == WebRuleStatus.ALLOWED },
                todayUsageMinutes = todayTotal,
                onBlockWebsite = viewModel::blockWebsite,
                onOpenBlocked = { navController.navigate(WebDestinations.BLOCKED) },
                onOpenAllowed = { navController.navigate(WebDestinations.ALLOWED) },
                onOpenRecent = { navController.navigate(WebDestinations.RECENT) },
                onOpenAnalytics = { navController.navigate(WebDestinations.ANALYTICS) },
            )
        }

        composable(WebDestinations.BLOCKED) {
            WebBlockedScreen(
                rules = state.webRules.filter { it.status == WebRuleStatus.BLOCKED },
                existingDomains = existingDomains,
                onUnblock = { viewModel.setWebRuleStatus(it.domain, WebRuleStatus.ALLOWED) },
                onDelete = { viewModel.removeWebRule(it.domain) },
                onAdd = { domain, status -> viewModel.addWebRule(domain, status) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(WebDestinations.ALLOWED) {
            WebAllowedScreen(
                rules = state.webRules.filter { it.status == WebRuleStatus.ALLOWED },
                existingDomains = existingDomains,
                onBlock = { viewModel.setWebRuleStatus(it.domain, WebRuleStatus.BLOCKED) },
                onRemove = { viewModel.removeWebRule(it.domain) },
                onAdd = { domain, status -> viewModel.addWebRule(domain, status) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(WebDestinations.RECENT) {
            WebRecentScreen(
                rules = state.webRules.sortedByDescending { it.updatedAt }.take(8),
                onToggleStatus = { rule ->
                    viewModel.setWebRuleStatus(
                        rule.domain,
                        if (rule.status == WebRuleStatus.BLOCKED) WebRuleStatus.ALLOWED else WebRuleStatus.BLOCKED,
                    )
                },
                onDelete = { viewModel.removeWebRule(it.domain) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(WebDestinations.ANALYTICS) {
            WebAnalyticsScreen(
                period = state.webPeriod,
                onPeriodChange = viewModel::setWebPeriod,
                summary = summary,
                onBack = { navController.popBackStack() },
            )
        }
    }

    // System Back pops every secondary Web screen; at the blocking root it
    // falls through to the standard tab behavior (same as the other tabs).
    BackHandler(enabled = currentRoute != WebDestinations.BLOCKING) {
        navController.popBackStack()
    }
}
