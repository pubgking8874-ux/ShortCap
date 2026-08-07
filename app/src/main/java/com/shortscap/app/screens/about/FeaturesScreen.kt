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
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
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
                iconKey = IconKey.FEATURE_APP_BLOCKING,
                title = strings.featureAppBlocking,
                subtitle = strings.featureAppBlockingText,
            )
            ScPremiumInfoCard(
                iconKey = IconKey.FEATURE_USAGE_TRACKING,
                title = strings.featureUsageTracking,
                subtitle = strings.featureUsageTrackingText,
            )
            ScPremiumInfoCard(
                iconKey = IconKey.FEATURE_FOCUS_MODE,
                title = strings.featureFocusMode,
                subtitle = strings.featureFocusModeText,
            )
            ScPremiumInfoCard(
                iconKey = IconKey.FEATURE_WELLBEING,
                title = strings.featureDigitalWellbeing,
                subtitle = strings.featureDigitalWellbeingText,
            )
            ScPremiumInfoCard(
                iconKey = IconKey.FEATURE_SECURE_AUTH,
                title = strings.featureSecureAuth,
                subtitle = strings.featureSecureAuthText,
            )
        }
    }
}
