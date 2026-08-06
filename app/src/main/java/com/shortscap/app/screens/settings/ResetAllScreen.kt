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
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors

/**
 * Reset All Settings — dedicated screen: explains what a reset does and asks
 * for confirmation. Future: also clears backend / cloud preferences through
 * the SettingsRepository seam.
 */
@Composable
fun ResetAllScreen(
    onResetAll: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = "Reset All Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumInfoCard(
                icon = Icons.Filled.RestartAlt,
                title = "Reset All Settings",
                subtitle = "Restore monitoring, notifications and every other " +
                    "setting to their default values. This cannot be undone.",
                modifier = Modifier.fillMaxWidth(),
            )
            ScButton(
                label = "Reset All Settings",
                variant = ScButtonVariant.DANGER,
                onClick = onResetAll,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
