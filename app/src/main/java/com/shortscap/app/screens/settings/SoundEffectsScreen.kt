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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.sounds.SoundEffectCategory
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Sound & Effects — the CENTRAL app-sounds control center, all on ONE screen.
 *
 * Deliberately separate from the Android device Sound / Vibrate / Silent
 * mode (owned by Study Mode → Sound Mode). The page shows every ShortsCap
 * sound organized under three section headings — STUDY MODE, MONITORING and
 * NOTIFICATIONS. Headings are pure labels (never navigate); the small ⓘ
 * next to each opens a compact in-place info popup. Every row below shows
 * ONLY the sound/event name and opens the shared sound configuration screen
 * — the currently selected audio is deliberately NOT shown here; users
 * discover it inside each option's configuration screen.
 *
 * Selection is persisted in the single [SoundEffectsConfig] held by the
 * ViewModel (backend-ready via SoundEffectsRepository).
 */
@Composable
fun SoundEffectsScreen(
    onOpenSound: (SoundEffectCategory) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var infoGroup by remember { mutableStateOf<SoundGroup?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.soundEffectsTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Three sections on ONE screen — headings only, no cards. ----
            SoundGroup.entries.forEach { group ->
                SoundSection(
                    group = group,
                    onOpenSound = onOpenSound,
                    onInfo = { infoGroup = group },
                )
            }
        }
    }

    // ---- Section info popup — compact, in place, never navigates. ----
    infoGroup?.let { group ->
        SectionInfoDialog(
            group = group,
            onDismiss = { infoGroup = null },
        )
    }
}

/**
 * One section: a non-clickable heading (icon + uppercased title + ⓘ button)
 * above a single grouped container holding that section's compact sound
 * rows, separated by thin dividers — the premium grouped-settings look.
 */
@Composable
private fun SoundSection(
    group: SoundGroup,
    onOpenSound: (SoundEffectCategory) -> Unit,
    onInfo: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val style = LocalIconStyle.current
    val shape = RoundedCornerShape(20.dp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ---- Section heading — label only, never navigates. ----
        val headingIcon = IconTheme.icon(style, groupIconKey(group))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
        ) {
            Icon(
                headingIcon,
                contentDescription = null,
                tint = IconTheme.tint(style, groupIconKey(group), colors.Accent),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                soundGroupTitle(strings, group).uppercase(),
                color = colors.TextSecondary,
                style = ScTextStyles.SectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Small circular ⓘ — opens the section info popup.
            Box(
                modifier = Modifier
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
                    contentDescription = strings.soundInfoButton,
                    tint = colors.TextSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.Card, shape)
                .border(1.dp, colors.Divider, shape),
        ) {
            group.categories.forEachIndexed { index, category ->
                SoundRow(
                    category = category,
                    onClick = { onOpenSound(category) },
                )
                if (index < group.categories.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp)
                            .height(1.dp)
                            .background(colors.Divider),
                    )
                }
            }
        }
    }
}

/** Compact settings row — icon · sound/event name · chevron only. */
@Composable
private fun SoundRow(
    category: SoundEffectCategory,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                categoryIcon(category),
                contentDescription = null,
                tint = colors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            categoryLabel(strings, category),
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Compact premium info popup — title, short explanation, OK to dismiss. */
@Composable
private fun SectionInfoDialog(
    group: SoundGroup,
    onDismiss: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextSecondary,
        title = { Text(sectionInfoTitle(strings, group)) },
        text = {
            Text(
                sectionInfoDescription(strings, group),
                style = ScTextStyles.Body,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.ok, color = colors.Accent)
            }
        },
    )
}

/** Icon key behind each section heading. */
private fun groupIconKey(group: SoundGroup): IconKey = when (group) {
    SoundGroup.STUDY -> IconKey.STUDY_MODE
    // Usage monitoring / activity tracking — analytics line, not the eye icon.
    SoundGroup.MONITORING -> IconKey.MONITORING_ANALYTICS
    SoundGroup.NOTIFICATIONS -> IconKey.NOTIFICATIONS
}
