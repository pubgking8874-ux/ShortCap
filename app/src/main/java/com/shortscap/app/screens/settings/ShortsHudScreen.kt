package com.shortscap.app.screens.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.R
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.hud.ShortsHudAppearance
import com.shortscap.app.hud.ShortsHudController
import com.shortscap.app.hud.ShortsHudSettingsStore
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Shorts HUD appearance settings — opened from Settings → Short Control →
 * Shorts HUD (the single canonical location for every Shorts setting).
 *
 * Lets the user pick exactly ONE of the three Shorts HUD appearance modes:
 *
 *   1. Brain      — animated brain (the four final brain videos drive the
 *                   runtime HUD; this page shows an animated concept preview).
 *   2. Counter    — the cleanest minimal counter, e.g. "127 / 200".
 *   3. ShortsCap  — the branded chip: ShortsCap logo + count / limit.
 *
 * Every option is shown with a small HUD-like visual preview and a radio
 * indicator (○/●), exactly one can be selected, and the choice is persisted
 * through the existing SharedPreferences-based [ShortsHudSettingsStore] so it
 * survives app restarts and navigation (same storage as theme/language).
 *
 * This screen is UI-only: it does not start the floating overlay, request the
 * overlay permission, count Shorts, or play brain state videos here — the
 * runtime HUD is driven entirely by the existing Shorts detection pipeline
 * and [ShortsHudController]. The selected appearance is only pushed to the
 * controller so a visible overlay switches mode immediately.
 */
@Composable
fun ShortsHudScreen(onBack: () -> Unit) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val store = remember { ShortsHudSettingsStore(context) }

    var appearance by remember { mutableStateOf(store.appearance()) }

    fun select(newAppearance: ShortsHudAppearance) {
        if (newAppearance == appearance) return
        appearance = newAppearance
        store.setAppearance(newAppearance)
        // Push the new appearance to the running controller so an overlay
        // already on screen switches mode immediately (no-op when hidden).
        ShortsHudController.refresh()
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.shortsHudTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle(strings.shortsHudAppearance)

            AppearanceOption(
                label = strings.shortsHudAppearanceBrain,
                preview = {
                    BrainPreview(
                        previewDesc = strings.shortsHudPreviewBrain,
                    )
                },
                selected = appearance == ShortsHudAppearance.BRAIN,
                onClick = { select(ShortsHudAppearance.BRAIN) },
            )
            AppearanceOption(
                label = strings.shortsHudAppearanceLiveCounter,
                preview = {
                    CounterPreview(
                        previewDesc = strings.shortsHudPreviewCounter,
                        value = strings.shortsHudPreviewCounterValue,
                    )
                },
                selected = appearance == ShortsHudAppearance.LIVE_COUNTER,
                onClick = { select(ShortsHudAppearance.LIVE_COUNTER) },
            )
            AppearanceOption(
                label = strings.shortsHudAppearanceShortsCap,
                preview = {
                    ShortsCapPreview(
                        previewDesc = strings.shortsHudPreviewShortsCap,
                        value = strings.shortsHudPreviewCounterValue,
                    )
                },
                selected = appearance == ShortsHudAppearance.SHORTSCAP,
                onClick = { select(ShortsHudAppearance.SHORTSCAP) },
            )
        }
    }
}

/**
 * One selectable Shorts HUD appearance row — preview chip · label · radio
 * indicator, highlighted when [selected]. The whole row is a Material radio
 * button for accessibility (option name + selected state, never color only);
 * the visual radio dot is marked decorative so the row reads as one node.
 */
@Composable
private fun AppearanceOption(
    label: String,
    preview: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.ChipActiveBg else colors.Card, shape)
            .border(
                width = 1.dp,
                color = if (selected) colors.Accent.copy(alpha = 0.6f) else colors.Divider,
                shape = shape,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        preview()
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = if (selected) colors.ChipActiveText else colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * Brain preview — a compact HUD-like chip showing the CONCEPT of an animated
 * brain state (a gently pulsing brain icon). It deliberately does NOT play
 * the brain videos: the four final brain videos belong to the live HUD
 * runtime and are only played by the overlay, never in settings.
 */
@Composable
private fun BrainPreview(previewDesc: String) {
    val colors = LocalScColors.current

    // Calm, infinite breathing — the "animated brain state" concept.
    val transition = rememberInfiniteTransition(label = "brainPreviewPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "brainPreviewPulseValue",
    )

    PreviewChip(
        previewDesc = previewDesc,
    ) {
        Icon(
            // Clean brain silhouette dedicated to this option (ic_brain.xml
            // stays the notification small icon — untouched).
            painter = painterResource(R.drawable.ic_brain_option),
            contentDescription = null,
            tint = colors.Success,
            modifier = Modifier.size(26.dp).scale(pulse),
        )
    }
}

/**
 * Counter preview — the clean mock example "127 / 200" (spec-mandated mock
 * preview data only; the live HUD shows the real count/limit).
 */
@Composable
private fun CounterPreview(previewDesc: String, value: String) {
    val colors = LocalScColors.current
    PreviewChip(previewDesc = previewDesc) {
        Text(
            text = value,
            color = colors.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * ShortsCap preview — the existing application branding (logo_pic) inside a
 * small HUD-like chip, mirroring the real branded HUD chip (logo + count).
 */
@Composable
private fun ShortsCapPreview(previewDesc: String, value: String) {
    val colors = LocalScColors.current
    PreviewChip(previewDesc = previewDesc) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logo_pic),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "  $value",
                color = colors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * The shared small HUD-like preview container. [previewDesc] becomes the
 * preview's accessibility content description (merged into the parent radio
 * row, so screen readers announce name + selected state + preview).
 */
@Composable
private fun PreviewChip(
    previewDesc: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .semantics { contentDescription = previewDesc }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
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
