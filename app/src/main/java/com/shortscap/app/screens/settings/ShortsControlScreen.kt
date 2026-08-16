package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.theme.LocalScColors

/**
 * Short Control — the SINGLE canonical home for every Shorts-related setting
 * (Settings → Short Control). Exactly four sub-sections, each on its own page:
 *
 *   1. Short Applications — per-platform Shorts monitoring toggles
 *   2. Shorts Limit       — the authoritative 24-hour cycle (current count,
 *                           remaining time, circular progress, limit picker)
 *   3. Shorts HUD         — Brain / Counter / ShortsCap appearance
 *   4. Shorts Insights    — read-only usage summaries (Yesterday / Today /
 *                           This Week / This Month)
 *
 * This hub only navigates — it owns no state. All state lives in the existing
 * engines and stores (platform list in MonitoringSettings.platforms, the
 * 24-hour cycle in ShortsControlEngine, HUD appearance in
 * ShortsHudSettingsStore), so there is exactly one authoritative location for
 * every Shorts setting and no duplicated controls anywhere else in Settings.
 */
@Composable
fun ShortsControlScreen(
    onOpenApplications: () -> Unit,
    onOpenLimit: () -> Unit,
    onOpenHud: () -> Unit,
    onOpenInsights: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.settingsShortControl, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumNavCard(
                iconKey = IconKey.SHORTS_APPLICATIONS,
                title = strings.shortsApplications,
                subtitle = strings.shortsApplicationsDesc,
                onClick = onOpenApplications,
                modifier = Modifier.fillMaxWidth(),
            )
            ScPremiumNavCard(
                iconKey = IconKey.SHORTS_LIMIT,
                title = strings.shortsLimitTitle,
                onClick = onOpenLimit,
                modifier = Modifier.fillMaxWidth(),
            )
            ScPremiumNavCard(
                iconKey = IconKey.SHORTS_HUD,
                title = strings.shortsHudTitle,
                subtitle = strings.shortsHudDesc,
                onClick = onOpenHud,
                modifier = Modifier.fillMaxWidth(),
            )
            ScPremiumNavCard(
                iconKey = IconKey.SHORTS_INSIGHTS,
                title = strings.shortsInsights,
                subtitle = strings.shortsInsightsDesc,
                onClick = onOpenInsights,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
