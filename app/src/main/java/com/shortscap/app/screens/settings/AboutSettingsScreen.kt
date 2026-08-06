package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScInfoRow
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors

/**
 * About (Settings) — dedicated screen with the app version and build info.
 * Keeps the existing About content, moved from the old inline expansion.
 */
@Composable
fun AboutSettingsScreen(onBack: () -> Unit) {
    val colors = LocalScColors.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = "About", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumInfoCard(
                icon = Icons.Filled.Info,
                title = "ShortsCap",
                subtitle = "Digital wellbeing companion for short-form video.",
                modifier = Modifier.fillMaxWidth(),
            )
            ScCard(modifier = Modifier.fillMaxWidth()) {
                ScInfoRow(label = "Version", value = "2.4.1")
                ScInfoRow(label = "Build", value = "2026072801")
                ScInfoRow(label = "Copyright", value = "© 2026 ShortsCap")
            }
        }
    }
}
