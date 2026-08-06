package com.shortscap.app.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Technologies — the stack behind ShortsCap in premium info cards. Python
 * Backend and AWS Cloud are marked as planned ("Future"); the row model can
 * later be driven by a backend-hosted list without redesigning the screen.
 */
@Composable
fun TechnologiesScreen(
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = strings.techTitle, onBack = onBack)

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
                icon = Icons.Filled.Android,
                title = strings.techAndroid,
                subtitle = strings.techAndroidText,
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Code,
                title = strings.techKotlin,
                subtitle = strings.techKotlinText,
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Widgets,
                title = strings.techCompose,
                subtitle = strings.techComposeText,
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Storage,
                title = strings.techPython,
                subtitle = strings.techPythonText,
                trailing = { FuturePill(strings.future) },
            )
            ScPremiumInfoCard(
                icon = Icons.Filled.Cloud,
                title = strings.techAws,
                subtitle = strings.techAwsText,
                trailing = { FuturePill(strings.future) },
            )
        }
    }
}

@Composable
private fun FuturePill(label: String) {
    val colors = LocalScColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.ChipActiveBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = colors.ChipActiveText, style = ScTextStyles.Caption)
    }
}
