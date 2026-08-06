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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = "About", onBack = onBack)

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
                icon = Icons.Filled.Flag,
                title = "Mission",
                subtitle = "To help people reclaim their attention by making digital wellbeing simple, transparent and effective for everyone.",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Visibility,
                title = "Vision",
                subtitle = "A world where technology works for you — giving you clarity over your time and the freedom to build healthier habits.",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.TrackChanges,
                title = "Purpose",
                subtitle = "To turn screen time from an unconscious habit into a deliberate choice — one focused day at a time.",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Info,
                title = "ShortsCap Introduction",
                subtitle = "ShortsCap is a digital wellbeing companion that helps you take control of your screen time. It tracks app usage, blocks distracting apps and sites, and supports healthier digital habits — so you can focus on what matters.",
            )
        }
    }
}
