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
 * About — ShortsCap Mission, Vision, Purpose and Introduction in premium
 * info cards. Static copy today; each section can later be driven by backend
 * content without any UI redesign.
 */
@Composable
fun AboutInfoScreen(
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = strings.aboutTitle, onBack = onBack)

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
                iconKey = IconKey.ABOUT_MISSION,
                title = strings.aboutMission,
                subtitle = strings.aboutMissionText,
            )
            ScPremiumInfoCard(
                iconKey = IconKey.ABOUT_VISION,
                title = strings.aboutVision,
                subtitle = strings.aboutVisionText,
            )
            ScPremiumInfoCard(
                iconKey = IconKey.ABOUT_PURPOSE,
                title = strings.aboutPurpose,
                subtitle = strings.aboutPurposeText,
            )
            ScPremiumInfoCard(
                iconKey = IconKey.ABOUT_INTRO,
                title = strings.aboutIntro,
                subtitle = strings.aboutIntroText,
            )
        }
    }
}
