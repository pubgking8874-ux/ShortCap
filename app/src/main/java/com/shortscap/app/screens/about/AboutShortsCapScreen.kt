package com.shortscap.app.screens.about

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
 * About ShortsCap — hub menu. Shows ONLY the five premium navigation cards,
 * each opening its own dedicated screen (About / Features / Technologies /
 * Version & Build / Copyright) via the drawer back stack ([DrawerNavHost]).
 */
@Composable
fun AboutShortsCapScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFeatures: () -> Unit,
    onOpenTechnologies: () -> Unit,
    onOpenVersionBuild: () -> Unit,
    onOpenCopyright: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = strings.aboutHubTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumNavCard(
                iconKey = IconKey.ABOUT_INFO,
                title = strings.aboutHubAbout,
                onClick = onOpenAbout,
            )
            ScPremiumNavCard(
                iconKey = IconKey.ABOUT_FEATURES,
                title = strings.aboutHubFeatures,
                onClick = onOpenFeatures,
            )
            ScPremiumNavCard(
                iconKey = IconKey.ABOUT_TECHNOLOGIES,
                title = strings.aboutHubTechnologies,
                onClick = onOpenTechnologies,
            )
            ScPremiumNavCard(
                iconKey = IconKey.ABOUT_VERSION_BUILD,
                title = strings.aboutHubVersionBuild,
                onClick = onOpenVersionBuild,
            )
            ScPremiumNavCard(
                iconKey = IconKey.ABOUT_COPYRIGHT,
                title = strings.aboutHubCopyright,
                onClick = onOpenCopyright,
            )
        }
    }
}
