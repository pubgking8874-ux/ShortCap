package com.shortscap.app.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScCircularAnalyticsCarousel
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScEntityRow
import com.shortscap.app.components.ScSkeleton
import com.shortscap.app.components.ScStatCard
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.model.ScCircularMetric
import com.shortscap.app.model.ScEntity
import com.shortscap.app.model.ScEntityType
import com.shortscap.app.permissions.PermissionActions
import com.shortscap.app.permissions.PermissionId
import com.shortscap.app.screens.settings.permissionTitle
import com.shortscap.app.screens.web.formatWebDuration
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScChrome
import com.shortscap.app.theme.ScInstagram
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.theme.ScWhatsApp

// Timestamps are mock data; they follow the language catalog so the Recent
// Activity rows never show a stale language.
private fun recentActivity(strings: com.shortscap.app.i18n.AppStrings) = listOf(
    ScEntity(id = "instagram", title = "Instagram", type = ScEntityType.APP, packageName = "com.instagram.android", usageTime = "42m", timestamp = strings.homeRecentTime1, fallbackColor = ScInstagram),
    ScEntity(id = "chrome", title = "Chrome", type = ScEntityType.APP, packageName = "com.android.chrome", usageTime = "28m", timestamp = strings.homeRecentTime2, fallbackColor = ScChrome),
    ScEntity(id = "whatsapp", title = "WhatsApp", type = ScEntityType.APP, packageName = "com.whatsapp", usageTime = "15m", timestamp = strings.homeRecentTime3, fallbackColor = ScWhatsApp),
)

/**
 * Mirrors function HomeScreen({ loading }) { ... }.
 *
 * Quick Stats read the SAME centralized data the Activity and Web screens
 * use ([AppUiState.homeAppsUsedToday] + the web rule counts) and each card
 * opens its real screen — no separate Home data or fake values.
 *
 * Monitoring Paused (priority state): when [monitoringPaused] is true (a
 * required monitoring permission is missing — derived centrally in
 * AppUiState), a Monitoring Paused section becomes the FIRST swipeable page.
 * Watch Time and Shorts Count move behind it but stay reachable by swiping,
 * and the section disappears automatically once the permissions are granted.
 *
 * The Monitoring Paused CIRCLE itself is the start/resume control (no button):
 * tapping it re-checks the required permissions ([onRefreshPermissions]) and,
 * while any are still missing, shows the permission-requirement popup that
 * routes to the Android settings screen for the first missing one. Monitoring
 * resumes only after a re-verified grant (automatic app-resume refresh).
 */
@Composable
fun HomeScreen(
    loading: Boolean,
    metrics: List<ScCircularMetric>,
    // Today's total usage MINUTES — derived in AppUiState from the SAME
    // ActivityRepository Daily report the Activity → Daily chart renders, so
    // the Home card and the Daily Activity timeline can never disagree.
    todayUsageMinutes: Int,
    appsUsedToday: Int,
    blockedWebCount: Int,
    allowedWebCount: Int,
    monitoringPaused: Boolean = false,
    missingRequiredPermissions: List<PermissionId> = emptyList(),
    // Study Mode active state — timestamp-based remaining time from
    // AppUiState; when a session runs, the carousel leads with the Study Mode
    // page (countdown + Watch/Timer animation) and the Shorts pages move
    // behind it. The page is TAPPABLE: it opens the "Stop Study Mode?"
    // confirmation, whose confirm action leads to the SHARED Focus Exit
    // Passcode verification (same screen as General → Study Mode).
    studyModeActive: Boolean = false,
    studyRemainingMillis: Long = 0L,
    studyTotalMillis: Long = 0L,
    onStopStudyMode: () -> Unit,
    onRefreshPermissions: () -> Unit,
    // Fired when no Android settings screen could be opened for a missing
    // permission (e.g. the Accessibility settings screen is unavailable) —
    // the UI shows a professional fallback message instead of failing.
    onPermissionSettingsUnavailable: () -> Unit,
    onOpenActivityDaily: () -> Unit,
    onOpenWebAllowed: () -> Unit,
    onOpenWebBlocked: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var resumeDialogOpen by remember { mutableStateOf(false) }
    var stopStudyDialogOpen by remember { mutableStateOf(false) }

    // If the paused state clears while the popup is open (e.g. the user
    // granted the permission via Android Settings without tapping Continue),
    // drop the stale open flag so the popup can never resurface on a later
    // pause without a fresh tap on the circle.
    LaunchedEffect(monitoringPaused) {
        if (!monitoringPaused) resumeDialogOpen = false
    }
    // If Study Mode ends (naturally at 00:00) while the stop confirmation is
    // open, drop it — there is nothing to stop anymore.
    LaunchedEffect(studyModeActive) {
        if (!studyModeActive) stopStudyDialogOpen = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Text(strings.homeGreeting, color = colors.TextSecondary, style = ScTextStyles.Label)
            Text("Arjun \uD83D\uDC4B", color = colors.TextPrimary, style = ScTextStyles.H1)
        }

        if (loading) {
            ScSkeleton(height = 290.dp)
        } else {
            ScCircularAnalyticsCarousel(
                metrics = metrics,
                // The Monitoring Paused section is injected as a priority
                // swipe page only while monitoring is genuinely paused; the
                // existing Watch Time / Shorts Count pages remain untouched.
                monitoringPaused = monitoringPaused,
                // The whole paused circle is the control: first re-verify the
                // required permissions, then surface the requirement popup.
                onResumeMonitoring = if (monitoringPaused) {
                    {
                        onRefreshPermissions()
                        resumeDialogOpen = true
                    }
                } else null,
                // While Study Mode is active, the Study Mode page (countdown
                // + Watch/Timer animation) leads the carousel; the Shorts
                // monitoring pages stay behind it and return to the front at
                // 00:00. Tapping the page only opens the "Stop Study Mode?"
                // confirmation — it never stops the session directly.
                studyModeActive = studyModeActive,
                studyRemainingMillis = studyRemainingMillis,
                studyTotalMillis = studyTotalMillis,
                onStopStudyMode = { stopStudyDialogOpen = true },
            )
        }

        Column {
            Text(strings.homeQuickStats, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
            Spacer(Modifier.height(12.dp))
            if (loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScSkeleton(height = 100.dp, modifier = Modifier.weight(1f))
                    ScSkeleton(height = 100.dp, modifier = Modifier.weight(1f))
                    ScSkeleton(height = 100.dp, modifier = Modifier.weight(1f))
                }
            } else {
                // Four fully clickable Quick Status cards in a responsive 2x2
                // grid (identical size/height/visual weight — ScStatCard gives
                // every card the same fixed structure). Each card opens its
                // real screen: Today Usage + Apps Used → Activity Daily (the
                // Today Usage value is the SAME daily total the Activity chart
                // renders), Allowed/Blocked Websites → their Web screens. All
                // values come from the shared data layer — never hardcoded.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScStatCard(
                        iconKey = IconKey.STAT_TODAY_USAGE,
                        label = strings.homeTodayUsage,
                        value = formatWebDuration(todayUsageMinutes, strings),
                        onClick = onOpenActivityDaily,
                        modifier = Modifier.weight(1f),
                    )
                    ScStatCard(
                        iconKey = IconKey.STAT_APPS_USED,
                        label = strings.homeAppsUsed,
                        value = "$appsUsedToday",
                        onClick = onOpenActivityDaily,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScStatCard(
                        iconKey = IconKey.WEB_ALLOWED,
                        label = strings.webAllowedTitle,
                        value = "$allowedWebCount",
                        accent = colors.Success,
                        onClick = onOpenWebAllowed,
                        modifier = Modifier.weight(1f),
                    )
                    ScStatCard(
                        iconKey = IconKey.STAT_BLOCKED_SITES,
                        label = strings.homeBlockedSites,
                        value = "$blockedWebCount",
                        accent = colors.Warning,
                        onClick = onOpenWebBlocked,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.homeRecentActivity, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
                Text(strings.homeSeeAll, color = colors.Accent, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            ScCard(modifier = Modifier.fillMaxWidth()) {
                val items = recentActivity(strings)
                items.forEachIndexed { index, item ->
                    ScEntityRow(
                        entity = item,
                        subtitle = item.timestamp,
                        trailing = {
                            Text(item.usageTime ?: "", color = colors.TextSecondary, fontSize = 13.sp)
                        },
                    )
                    if (index < items.size - 1) ScDivider()
                }
            }
        }
    }

    // "Stop Study Mode?" confirmation — opened by tapping the active Study
    // Mode page. It does NOT stop Study Mode: the confirm action opens the
    // SHARED Exit Passcode verification screen, and Study Mode ends
    // only after the passcode is verified (or 00:00 is reached naturally).
    if (stopStudyDialogOpen) {
        AlertDialog(
            onDismissRequest = { stopStudyDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.studyStopTitle) },
            text = { Text(strings.studyStopMessage, style = ScTextStyles.Body) },
            confirmButton = {
                TextButton(onClick = {
                    stopStudyDialogOpen = false
                    onStopStudyMode()
                }) {
                    Text(strings.studyStopAction, color = colors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { stopStudyDialogOpen = false }) {
                    Text(strings.cancel, color = colors.TextSecondary)
                }
            },
        )
    }

    // Professional permission-requirement popup shown when the user taps the
    // Monitoring Paused circle. It explains that required permissions must be
    // enabled first and routes to the Android settings page for the first
    // missing one. The popup is guarded by [monitoringPaused], so it only
    // stays visible while a required permission is genuinely missing — if the
    // tap's re-check found everything granted (e.g. granted elsewhere), the
    // section is already gone and no popup appears. The section only
    // disappears AFTER the permission status is re-verified as granted
    // (automatic app-resume refresh) — never merely because the settings
    // screen was opened.
    if (resumeDialogOpen && monitoringPaused) {
        AlertDialog(
            onDismissRequest = { resumeDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.resumeMonitoringDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.resumeMonitoringDialogMessage, style = ScTextStyles.Body)
                    if (missingRequiredPermissions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                strings.resumeMonitoringDialogRequired,
                                color = colors.TextPrimary,
                                style = ScTextStyles.BodySemiBold,
                            )
                            missingRequiredPermissions.forEach { id ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = colors.Warning,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        permissionTitle(id, strings),
                                        color = colors.TextPrimary,
                                        style = ScTextStyles.Body,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Open the Android settings page for the first missing
                        // required permission (Accessibility falls back to the
                        // app-details route automatically). If NOTHING could
                        // be opened, inform the user with a fallback message.
                        val opened = missingRequiredPermissions.firstOrNull()?.let {
                            PermissionActions.open(context, it)
                        } ?: false
                        if (!opened) onPermissionSettingsUnavailable()
                        resumeDialogOpen = false
                    },
                ) {
                    Text(strings.resumeMonitoringDialogContinue, color = colors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { resumeDialogOpen = false }) {
                    Text(strings.resumeMonitoringDialogNotNow, color = colors.TextSecondary)
                }
            },
        )
    }
}
