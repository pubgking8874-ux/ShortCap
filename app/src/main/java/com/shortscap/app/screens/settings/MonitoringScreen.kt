package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.model.MonitoringSettings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Monitoring Settings — the dedicated screen for every monitoring feature.
 *
 * The page is a clean configuration hub (no statistics — Home holds the quick
 * summary, Activity the detailed reports):
 *
 *   MONITORING       → Screen Activity (renamed from "Device Monitoring" —
 *                      master switch + Enabled/Disabled status + small
 *                      circular info button that explains what is monitored:
 *                      GENERAL app usage — which app is active and for how
 *                      long — never Shorts-specific detection — and why
 *                      permissions matter)
 *   STRICT MODE      → Strict Mode switch
 *   STUDY MODE       → Study Mode (relocated from the General section; kept
 *                      exactly as it was — same card, same design)
 *   MONITORING SCHEDULE → Monitoring Schedule page
 *
 * Break Reminder / Break Duration is NOT a separate monitoring item — it
 * belongs exclusively to Study Mode (StudyModeScreen owns that feature),
 * which now lives in this section.
 *
 * App Blocking, Daily Screen Time Limit and per-platform Shorts toggles no
 * longer live here: Shorts controls were consolidated into their own
 * top-level Settings → Short Control section (Short Applications / Shorts
 * Limit / Shorts HUD / Shorts Insights) so there is exactly one canonical
 * location for every Shorts setting.
 *
 * All state is driven by [MonitoringSettings] passed from the ViewModel; the
 * screen never hardcodes business logic or text (all labels come from the
 * active language catalog), so GET / UPDATE Monitoring Settings backend APIs
 * and new languages plug in without UI changes. Screen Activity uses the
 * app-wide permission terminology — Enabled / Disabled — exactly like the
 * Permissions screen.
 */
@Composable
fun MonitoringScreen(
    settings: MonitoringSettings,
    // Centralized paused state (derived from the live permission list in
    // AppUiState) — Screen Activity shows Disabled whenever a required
    // permission is missing, even if the master switch is on.
    monitoringPaused: Boolean = false,
    onToggleMonitoring: (Boolean) -> Unit,
    onToggleStrictMode: (Boolean) -> Unit,
    onOpenStudyMode: () -> Unit,
    onOpenSchedule: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var deviceInfoDialogOpen by remember { mutableStateOf(false) }

    // Screen Activity state — the app-wide Enabled/Disabled vocabulary.
    val deviceMonitoringEnabled = settings.enabled && !monitoringPaused

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.monitoringTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Section 1 — Screen Activity (master switch + status) ----
            SectionTitle(strings.monitoringSection)
            DeviceMonitoringCard(
                iconKey = IconKey.MONITORING_ENABLE,
                title = strings.monitoringDevice,
                subtitle = strings.monitoringDeviceDesc,
                statusText = if (deviceMonitoringEnabled) strings.permStatusEnabled else strings.permStatusDisabled,
                statusColor = if (deviceMonitoringEnabled) colors.Success else colors.Warning,
                enabled = settings.enabled,
                onToggle = { onToggleMonitoring(!settings.enabled) },
                onInfo = { deviceInfoDialogOpen = true },
            )

            // ---- Section 2 — Strict Mode ----
            SectionTitle(strings.monitoringStrictMode)
            ScPremiumNavCard(
                iconKey = IconKey.STRICT_MODE,
                title = strings.monitoringStrictMode,
                subtitle = strings.monitoringStrictModeDesc,
                onClick = { onToggleStrictMode(!settings.strictModeEnabled) },
                trailing = {
                    ScSwitch(on = settings.strictModeEnabled, onToggle = { onToggleStrictMode(!settings.strictModeEnabled) })
                },
            )

            // ---- Study Mode — relocated from the General section. The entry
            //      keeps its exact original design (same card, icon, title);
            //      the complete feature lives on its own StudyModeScreen. ----
            ScPremiumNavCard(
                iconKey = IconKey.STUDY_MODE,
                title = strings.studyTitle,
                onClick = onOpenStudyMode,
            )

            // Shorts controls were consolidated into Settings → Short Control
            // (their own top-level section) — Monitoring now only carries
            // general monitoring settings. Shorts Control no longer lives here.

            // ---- Section 3 — Monitoring Schedule (dedicated page) ----
            SectionTitle(strings.monitoringSchedule)
            ScPremiumNavCard(
                iconKey = IconKey.SCHEDULE,
                title = strings.monitoringSchedule,
                onClick = onOpenSchedule,
            )
        }
    }

    // ---- Screen Activity information dialog ----
    if (deviceInfoDialogOpen) {
        AlertDialog(
            onDismissRequest = { deviceInfoDialogOpen = false },
            containerColor = colors.Card,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text(strings.monitoringDeviceInfoTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.monitoringDeviceInfoMessage, style = ScTextStyles.Body)
                    Text(strings.monitoringDeviceInfoPermission, style = ScTextStyles.Body)
                    Text(strings.monitoringDeviceInfoDisabled, style = ScTextStyles.Body)
                }
            },
            confirmButton = {
                TextButton(onClick = { deviceInfoDialogOpen = false }) {
                    Text(strings.ok, color = colors.Accent)
                }
            },
        )
    }

}

/**
 * Screen Activity card — icon tile + title + small circular info button +
 * one-line description + Enabled/Disabled status + master switch. The card
 * body toggles monitoring; the info circle opens the information dialog.
 */
@Composable
private fun DeviceMonitoringCard(
    iconKey: IconKey,
    title: String,
    subtitle: String,
    statusText: String,
    statusColor: Color,
    enabled: Boolean,
    onToggle: () -> Unit,
    onInfo: () -> Unit,
) {
    val colors = LocalScColors.current
    val style = LocalIconStyle.current
    val resolvedIcon = IconTheme.icon(style, iconKey)
    val resolvedTint = IconTheme.tint(style, iconKey, colors.Accent)
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Compact rounded-square icon tile (44dp) — same premium proportions.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(resolvedIcon, contentDescription = null, tint = resolvedTint, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = colors.TextPrimary,
                    style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Small circular information area — opens the info dialog.
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.CardHover)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onInfo,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = colors.TextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = colors.TextSecondary, style = ScTextStyles.Body, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Text(statusText, color = statusColor, style = ScTextStyles.Caption.copy(fontWeight = FontWeight.SemiBold))
        }
        ScSwitch(on = enabled, onToggle = onToggle)
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

