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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = "Features", onBack = onBack)

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
                title = "App Blocking",
                subtitle = "Block distracting apps and websites to keep your focus intact.",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.BarChart,
                title = "Usage Tracking",
                subtitle = "See exactly where your time goes with clear daily usage insights.",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.CenterFocusStrong,
                title = "Focus Mode",
                subtitle = "Pause distractions during dedicated focus sessions.",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Spa,
                title = "Digital Wellbeing",
                subtitle = "Build balanced screen-time habits with supportive, actionable feedback.",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Lock,
                title = "Secure Authentication",
                subtitle = "Your account and data stay protected with secure sign-in.",
            )
        }
    }
}
