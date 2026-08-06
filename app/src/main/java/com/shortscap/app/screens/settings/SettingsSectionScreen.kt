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
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors

/**
 * Generic dedicated screen for a Settings section (General, Permissions,
 * Privacy, Data Backup). Keeps every Settings row on its own page with the
 * premium card language; content slots in later behind the same UI via the
 * SettingsRepository seam. [extra] lets a section render additional cards
 * (e.g. the permission list) above the placeholder note.
 */
@Composable
fun SettingsSectionScreen(
    icon: ImageVector,
    title: String,
    description: String,
    onBack: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = LocalScColors.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = title, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumInfoCard(
                icon = icon,
                title = title,
                subtitle = description,
                modifier = Modifier.fillMaxWidth(),
            )
            extra?.invoke()
            ScEmptyState(
                icon = Icons.Filled.Build,
                title = "Coming soon",
                subtitle = "This section is backend-ready. The UI and navigation " +
                    "are in place — implementation will connect here without any " +
                    "redesign.",
            )
        }
    }
}
