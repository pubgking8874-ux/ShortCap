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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Spa
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors

/**
 * Features — headline ShortsCap capabilities in premium info cards.
 * Static copy today; the feature list can later be driven by a backend API
 * without changing the screen structure.
 */
@Composable
fun FeaturesScreen(
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = strings.featuresTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumInfoCard(
                icon = Icons.Filled.Block,
                title = strings.featureAppBlocking,
                subtitle = strings.featureAppBlockingText,
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.BarChart,
                title = strings.featureUsageTracking,
                subtitle = strings.featureUsageTrackingText,
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.CenterFocusStrong,
                title = strings.featureFocusMode,
                subtitle = strings.featureFocusModeText,
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Spa,
                title = strings.featureDigitalWellbeing,
                subtitle = strings.featureDigitalWellbeingText,
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Lock,
                title = strings.featureSecureAuth,
                subtitle = strings.featureSecureAuthText,
            )
        }
    }
}
