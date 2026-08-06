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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Contact Support — support channels (Email, Phone, Hours) in premium info
 * cards. Current stage: static placeholder values. Future: driven by a
 * backend support API without any UI redesign.
 */
@Composable
fun ContactSupportScreen(
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = "Contact Support", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Need Help?", color = colors.TextPrimary, style = ScTextStyles.H1)

            ScPremiumInfoCard(
                icon = Icons.Filled.Email,
                title = "Email",
                subtitle = "support@shortscap.app",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Call,
                title = "Phone",
                subtitle = "+91 91234 56789",
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Schedule,
                title = "Support Hours",
                subtitle = "Monday – Saturday\n9:00 AM – 6:00 PM IST",
            )
        }
    }
}
