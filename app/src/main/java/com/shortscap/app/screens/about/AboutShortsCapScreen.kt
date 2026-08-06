package com.shortscap.app.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = "About ShortsCap", onBack = onBack)

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
                icon = Icons.Filled.Info,
                title = "About",
                onClick = onOpenAbout,
            )
            ScPremiumNavCard(
                icon = Icons.Filled.Star,
                title = "Features",
                onClick = onOpenFeatures,
            )
            ScPremiumNavCard(
                icon = Icons.Filled.Tune,
                title = "Technologies",
                onClick = onOpenTechnologies,
            )
            ScPremiumNavCard(
                icon = Icons.Filled.Build,
                title = "Version & Build",
                onClick = onOpenVersionBuild,
            )
            ScPremiumNavCard(
                icon = Icons.Filled.Copyright,
                title = "Copyright",
                onClick = onOpenCopyright,
            )
        }
    }
}
