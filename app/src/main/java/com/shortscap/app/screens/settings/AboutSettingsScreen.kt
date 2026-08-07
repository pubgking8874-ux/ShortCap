package com.shortscap.app.screens.settings

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
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScInfoRow
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.theme.LocalScColors

/**
 * About (Settings) — dedicated screen with the app version/build info plus
 * the legal documents (Privacy Policy, Terms & Conditions). Each legal row
 * opens the existing bundled document through
 * [com.shortscap.app.screens.legal.LegalDocumentScreen]; no new documents are
 * created.
 */
@Composable
fun AboutSettingsScreen(
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsConditions: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.settingsAbout, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScCard(modifier = Modifier.fillMaxWidth()) {
                ScInfoRow(label = strings.versionLabel, value = "2.4.1")
                ScInfoRow(label = strings.buildLabel, value = "2026072801")
                ScInfoRow(label = strings.copyrightLine, value = strings.allRightsReserved)
            }

            ScPremiumNavCard(
                iconKey = IconKey.PRIVACY_POLICY,
                title = strings.legalPrivacy,
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier.fillMaxWidth(),
            )
            ScPremiumNavCard(
                iconKey = IconKey.TERMS_CONDITIONS,
                title = strings.legalTerms,
                onClick = onOpenTermsConditions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
