package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.study.DefaultStudyAllowedApps
import com.shortscap.app.study.DefaultStudyAllowedWebsites
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScCursorColor
import com.shortscap.app.theme.ScTextStyles

/**
 * Allowed Apps/Websites (Study Mode) — the items that stay accessible while a
 * Study Mode session is active. Apps and websites are toggled independently
 * (each app/website keeps its OWN on/off membership in the study allowed
 * lists), and new study-friendly websites can be added by domain. The
 * membership lists live in StudyModeSettings — data, never hardcoded in the
 * UI — so a future backend can sync them without screen changes.
 */
@Composable
fun StudyAllowedItemsScreen(
    allowedApps: List<String>,
    allowedWebsites: List<String>,
    onToggleApp: (String) -> Unit,
    onToggleWebsite: (String) -> Unit,
    onAddWebsite: (String) -> Boolean,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var websiteInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Default catalog + any user-added domains, defaults first, deduplicated.
    val websiteItems = remember(allowedWebsites) {
        val defaults = DefaultStudyAllowedWebsites.map { it.id to it.name }
        val added = allowedWebsites
            .filter { d -> DefaultStudyAllowedWebsites.none { it.id == d } }
            .map { it to it }
        (defaults + added).distinctBy { it.first }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.studyAllowedItems, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Allowed Apps ----
            SectionTitle(strings.studyAllowedApps)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DefaultStudyAllowedApps.forEach { item ->
                    AllowedItemRow(
                        name = item.name,
                        checked = item.id in allowedApps,
                        onToggle = { onToggleApp(item.id) },
                    )
                }
            }

            // ---- Allowed Websites ----
            SectionTitle(strings.studyAllowedWebsites)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                websiteItems.forEach { (domain, name) ->
                    AllowedItemRow(
                        name = name,
                        checked = domain in allowedWebsites,
                        onToggle = { onToggleWebsite(domain) },
                    )
                }

                // Add-website row.
                OutlinedTextField(
                    value = websiteInput,
                    onValueChange = {
                        websiteInput = it
                        errorText = null
                    },
                    label = { Text(strings.studyAllowedWebsitePlaceholder, color = colors.TextSecondary) },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it, color = colors.Danger) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.Accent,
                        unfocusedBorderColor = colors.Divider,
                        focusedLabelColor = colors.Accent,
                        unfocusedLabelColor = colors.TextSecondary,
                        cursorColor = ScCursorColor(),
                        focusedTextColor = colors.TextPrimary,
                        unfocusedTextColor = colors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                ScButton(
                    label = strings.studyAllowedAdd,
                    onClick = {
                        if (onAddWebsite(websiteInput)) {
                            websiteInput = ""
                            errorText = null
                        } else {
                            errorText = if (websiteInput.isBlank() || !websiteInput.contains(".")) {
                                strings.studyAllowedInvalid
                            } else {
                                strings.studyAllowedDuplicate
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One allowed item row — name + membership switch. */
@Composable
private fun AllowedItemRow(
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            name,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            modifier = Modifier.weight(1f),
        )
        ScSwitch(on = checked, onToggle = onToggle)
    }
}

/** Uppercased section heading, matching the app's section-title style. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = LocalScColors.current.TextSecondary,
        style = ScTextStyles.SectionTitle,
    )
}
