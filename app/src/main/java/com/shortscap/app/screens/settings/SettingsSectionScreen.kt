package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors

/**
 * Generic dedicated screen for a Settings section (Privacy, Data Backup).
 *
 * Follows the Settings design rule: no introductory information card — only
 * the top bar (back + title) and the page content. Content slots in later
 * behind the same UI via the SettingsRepository seam.
 */
@Composable
fun SettingsSectionScreen(
    title: String,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = title, onBack = onBack)

        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            ScEmptyState(
                icon = Icons.Filled.Build,
                title = strings.comingSoon,
                subtitle = strings.comingSoonDesc,
            )
        }
    }
}
