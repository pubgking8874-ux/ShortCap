package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.appearance.FontMode
import com.shortscap.app.charts.ChartStyle
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.theme.LocalScColors

/**
 * Appearance hub — premium rows (icon · title · chevron), each opening its
 * own dedicated page: Theme, Icons (the global icon style picker), Chart
 * (the global visualization style), Font (the global typography family) and
 * Text Size (global typography scale). The Chart and Font rows show a short
 * summary of the currently selected value. Consistent with the Settings
 * design rule: no intro cards, no expandable sections.
 *
 * The Shorts HUD appearance selector no longer lives here — it was
 * consolidated into Settings → Short Control → Shorts HUD, the single
 * canonical location for every Shorts setting.
 */
@Composable
fun AppearanceScreen(
    chartStyle: ChartStyle,
    fontMode: FontMode,
    onOpenTheme: () -> Unit,
    onOpenIcons: () -> Unit,
    onOpenChart: () -> Unit,
    onOpenFont: () -> Unit,
    onOpenTextSize: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.appearanceTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumNavCard(
                iconKey = IconKey.THEME,
                title = strings.appearanceTheme,
                onClick = onOpenTheme,
            )
            ScPremiumNavCard(
                iconKey = IconKey.ICONS,
                title = strings.appearanceIcons,
                onClick = onOpenIcons,
            )
            ScPremiumNavCard(
                iconKey = IconKey.CHART,
                title = strings.appearanceChart,
                subtitle = chartStyle.displayName(strings),
                onClick = onOpenChart,
            )
            ScPremiumNavCard(
                iconKey = IconKey.FONT,
                title = strings.appearanceFont,
                subtitle = fontMode.displayName(strings),
                onClick = onOpenFont,
            )
            ScPremiumNavCard(
                iconKey = IconKey.TEXT_SIZE,
                title = strings.appearanceTextSize,
                onClick = onOpenTextSize,
            )
        }
    }
}
