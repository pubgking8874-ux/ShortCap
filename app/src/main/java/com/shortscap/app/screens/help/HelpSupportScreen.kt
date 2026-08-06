package com.shortscap.app.screens.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors

/**
 * Help & Support — hub menu. Shows ONLY the three premium navigation cards,
 * each opening its own dedicated screen via the drawer back stack
 * ([DrawerNavHost]). No content is rendered on this page.
 */
@Composable
fun HelpSupportScreen(
    onBack: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenContactSupport: () -> Unit,
    onOpenReportBug: () -> Unit,
) {
    val colors = LocalScColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = "Help & Support", onBack = onBack)

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
                icon = Icons.Filled.HelpOutline,
                title = "Frequently Asked Questions",
                onClick = onOpenFaq,
            )
            ScPremiumNavCard(
                icon = Icons.Filled.SupportAgent,
                title = "Contact Support",
                onClick = onOpenContactSupport,
            )
            ScPremiumNavCard(
                icon = Icons.Filled.BugReport,
                title = "Report a Bug",
                onClick = onOpenReportBug,
            )
        }
    }
}
