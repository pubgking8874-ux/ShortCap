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
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.theme.LocalScColors

/**
 * Appearance hub — 3 premium rows (icon · title · chevron, no subtitles),
 * each opening its own dedicated page: Theme, Icons (the global icon style
 * picker) and Text Size (global typography scale). Consistent with the
 * Settings design rule: no intro cards, no expandable sections.
 */
@Composable
fun AppearanceScreen(
    onOpenTheme: () -> Unit,
    onOpenIcons: () -> Unit,
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
                iconKey = IconKey.TEXT_SIZE,
                title = strings.appearanceTextSize,
                onClick = onOpenTextSize,
            )
        }
    }
}
