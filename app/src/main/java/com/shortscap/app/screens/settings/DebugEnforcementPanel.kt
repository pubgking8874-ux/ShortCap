package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.shorts.DebugEnforcementSimulation
import com.shortscap.app.shorts.DebugEnforcementSnapshot
import com.shortscap.app.shorts.ShortsEnforcementState
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * DEBUG-ONLY enforcement simulation control surface (Settings → Short
 * Control). Composed ONLY when BuildConfig.DEBUG — release builds never
 * render it and the simulation's own entry points are BuildConfig.DEBUG-gated
 * as well, so a production user can never drive simulated Shorts, fake a
 * count or trigger enforcement.
 *
 * The panel drives the [DebugEnforcementSimulation], which keeps a strictly
 * separate in-memory simulated cycle and feeds it through the REAL
 * [com.shortscap.app.shorts.ShortsControlEngine] derivation and the REAL
 * production [com.shortscap.app.shorts.shouldRestrict] decision — the same
 * decision the production restriction overlay consumes.
 */
@Composable
fun DebugEnforcementPanel() {
    val colors = LocalScColors.current
    val sim = remember { DebugEnforcementSimulation() }
    var snap by remember { mutableStateOf(sim.snapshot()) }
    // Numeric inputs — pre-filled from the real configured limit (read-only).
    var limitText by remember { mutableStateOf(snap.realLimit.takeIf { it > 0 }?.toString() ?: "") }
    var countText by remember { mutableStateOf("") }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Warning.copy(alpha = 0.6f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "ENFORCEMENT DEBUG (DEBUG BUILD ONLY)",
            color = colors.Warning,
            style = ScTextStyles.SectionTitle,
        )
        Text(
            "Simulates Shorts being watched and exercises the REAL enforcement " +
                "decision path. In-memory only — production count/limit/history are never touched.",
            color = colors.TextSecondary,
            style = ScTextStyles.Caption,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DebugStat("Real Limit", snap.realLimit.toString(), colors.CardHover, Modifier.weight(1f))
            DebugStat("Sim Count", snap.simulatedCount.toString(), colors.CardHover, Modifier.weight(1f))
            DebugStat("Sim Limit", snap.simulatedLimit.toString(), colors.CardHover, Modifier.weight(1f))
            DebugStat("Triggers", snap.triggerCount.toString(), colors.CardHover, Modifier.weight(1f))
        }

        Text(
            text = decisionLabel(snap),
            color = when {
                snap.restrictDecision -> colors.Danger
                snap.enforcementState == ShortsEnforcementState.WARNING -> colors.Warning
                else -> colors.TextPrimary
            },
            style = ScTextStyles.BodySemiBold,
        )

        if (!snap.active) {
            // --- Inactive: start a simulation (limit pre-filled from the real one) ---
            Text("Limit (defaults to your real limit)", color = colors.TextSecondary, style = ScTextStyles.Caption)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DebugNumberField(
                    text = limitText,
                    placeholder = snap.realLimit.takeIf { it > 0 }?.toString() ?: "50",
                    onTextChange = { limitText = it.filter { c -> c.isDigit() }.take(6) },
                    colors = colors,
                )
                DebugActionButton(
                    label = "START SIM",
                    accent = colors.Accent,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    snap = sim.start(limitText.toIntOrNull())
                    limitText = snap.simulatedLimit.takeIf { it > 0 }?.toString() ?: ""
                }
            }
        } else {
            // --- Active: walk the simulation up to / past the limit ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DebugNumberField(
                    text = countText,
                    placeholder = "count",
                    onTextChange = { countText = it.filter { c -> c.isDigit() }.take(6) },
                    colors = colors,
                )
                DebugActionButton(
                    label = "SET", accent = colors.Accent, colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    snap = sim.setCount(countText.toIntOrNull() ?: snap.simulatedCount)
                    countText = ""
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DebugActionButton(
                    label = "+1 SHORT", accent = colors.TextPrimary, colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    snap = sim.increment()
                }
                DebugActionButton(
                    label = "+5 SHORTS", accent = colors.TextPrimary, colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    snap = sim.incrementBy(5)
                }
                DebugActionButton(
                    label = if (snap.surfaceActive) "SURFACE ON" else "SURFACE OFF",
                    accent = if (snap.surfaceActive) colors.Danger else colors.TextSecondary,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    snap = sim.setSurfaceActive(!snap.surfaceActive)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DebugActionButton(
                    label = "RESET", accent = colors.Warning, colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    snap = sim.reset()
                }
                DebugActionButton(
                    label = "STOP", accent = colors.TextSecondary, colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    snap = sim.stop()
                }
            }
            Text(
                "Toggle SURFACE ON with Sim Count at/above the limit to watch " +
                    "SIM_ENFORCEMENT_TRIGGERED fire once in logcat (tag SC_DEBUG_ENFORCEMENT).",
                color = colors.TextDisabled,
                style = ScTextStyles.Caption,
            )
        }
    }
}

/** Human decision label — always reflects the REAL derived state. */
private fun decisionLabel(snap: DebugEnforcementSnapshot): String {
    if (!snap.active) return "SIMULATION OFF — production enforcement untouched"
    val surface = if (snap.surfaceActive) "surface active" else "surface NOT active"
    return when {
        snap.restrictDecision ->
            "Decision: RESTRICT (block) — count ${snap.simulatedCount}/${snap.simulatedLimit}, $surface"
        snap.enforcementState == ShortsEnforcementState.WARNING ->
            "Decision: WARNING — count ${snap.simulatedCount}/${snap.simulatedLimit}, $surface"
        else ->
            "Decision: NOT ENFORCED — count ${snap.simulatedCount}/${snap.simulatedLimit}, $surface"
    }
}

@Composable
private fun DebugStat(label: String, value: String, bg: Color, modifier: Modifier = Modifier) {
    val colors = LocalScColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(label.uppercase(), color = colors.TextSecondary, style = ScTextStyles.Caption, maxLines = 1)
        Text(value, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold.copy(fontSize = 14.sp), maxLines = 1)
    }
}

@Composable
private fun DebugActionButton(label: String, accent: Color, colors: ScColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = accent,
            style = ScTextStyles.ButtonLabel.copy(fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

/** Compact digit-only numeric field used by the debug panel. */
@Composable
private fun DebugNumberField(
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    colors: ScColors,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .width(96.dp)
            .clip(shape)
            .background(colors.CardHover, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isEmpty()) {
            Text(
                placeholder,
                color = colors.TextDisabled,
                style = ScTextStyles.Body.copy(fontSize = 12.sp),
                textAlign = TextAlign.Center,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = ScTextStyles.Body.copy(color = colors.TextPrimary, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(colors.Accent),
        )
    }
}
