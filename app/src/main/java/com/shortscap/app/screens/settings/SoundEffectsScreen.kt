package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.sounds.AppSound
import com.shortscap.app.sounds.SoundEffectCategory
import com.shortscap.app.sounds.SoundEffectsConfig
import com.shortscap.app.sounds.SoundPreviewPlayer
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Sound & Effects — the CENTRAL control panel for every ShortsCap app sound.
 *
 * This is deliberately separate from the Android device Sound / Vibrate /
 * Silent mode (owned by Study Mode → Sound Mode). Here the user controls
 * ShortsCap's OWN sounds: a master "App Sounds" switch plus one selectable
 * sound per category (Break Reminder, Study Schedule Reminder, Shorts limit
 * alerts, Break Start/End). Every future feature reads its sound from this
 * single [SoundEffectsConfig] — never per-feature sound systems.
 *
 * Preview (▶) plays ONLY that sound via [SoundPreviewPlayer] — short,
 * non-intrusive, and never triggering any actual reminder / notification /
 * Study Mode / Shorts-limit behavior.
 */
@Composable
fun SoundEffectsScreen(
    config: SoundEffectsConfig,
    onSetAppSoundsEnabled: (Boolean) -> Unit,
    onSetCategorySound: (SoundEffectCategory, AppSound) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var selectedCategory by remember { mutableStateOf<SoundEffectCategory?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.soundEffectsTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Master App Sounds switch — OFF silences every ShortsCap
            //      sound/effect (never touches the device audio mode). ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.Card, RoundedCornerShape(22.dp))
                    .border(1.dp, colors.Divider, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(colors.CardHover),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = colors.Accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.soundEffectsAppSounds, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp))
                    Text(strings.soundEffectsAppSoundsDesc, color = colors.TextSecondary, style = ScTextStyles.Caption)
                }
                ScSwitch(on = config.appSoundsEnabled, onToggle = { onSetAppSoundsEnabled(!config.appSoundsEnabled) })
            }

            // ---- Sound categories — dimmed + locked while App Sounds is OFF ----
            Column(
                modifier = Modifier.alpha(if (config.appSoundsEnabled) 1f else 0.4f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SoundEffectCategory.entries.forEach { category ->
                    SoundCategoryCard(
                        category = category,
                        sound = config.soundFor(category),
                        enabled = config.appSoundsEnabled,
                        onPreview = { SoundPreviewPlayer.play(config.soundFor(category)) },
                        onClick = { if (config.appSoundsEnabled) selectedCategory = category },
                    )
                }
            }
        }
    }

    // ---- Sound selection — one sound per category; every option can be
    //      previewed (▶) before selecting. ----
    selectedCategory?.let { category ->
        SoundPickerDialog(
            category = category,
            current = config.soundFor(category),
            onSelect = { sound ->
                onSetCategorySound(category, sound)
                selectedCategory = null
            },
            onDismiss = { selectedCategory = null },
        )
    }
}

/** One category row — current sound + small ▶ preview + chevron. */
@Composable
private fun SoundCategoryCard(
    category: SoundEffectCategory,
    sound: AppSound,
    enabled: Boolean,
    onPreview: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                categoryIcon(category),
                contentDescription = null,
                tint = colors.Accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                categoryLabel(strings, category),
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                soundLabel(strings, sound),
                color = colors.TextSecondary,
                style = ScTextStyles.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PreviewButton(enabled = enabled, onClick = onPreview)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Small circular ▶ — plays ONLY the requested sound. */
@Composable
private fun PreviewButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.CardHover)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = LocalAppStrings.current.soundEffectsPreview,
            tint = colors.Accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Sound picker — one row per [AppSound], preview + selected check. */
@Composable
private fun SoundPickerDialog(
    category: SoundEffectCategory,
    current: AppSound,
    onSelect: (AppSound) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextSecondary,
        title = { Text(categoryLabel(strings, category)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppSound.entries.forEach { sound ->
                    SoundOptionRow(
                        sound = sound,
                        selected = sound == current,
                        onPreview = { SoundPreviewPlayer.play(sound) },
                        onSelect = { onSelect(sound) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = colors.TextSecondary)
            }
        },
    )
}

/** One selectable sound row — name + ▶ preview + selected check. */
@Composable
private fun SoundOptionRow(
    sound: AppSound,
    selected: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.ChipActiveBg else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            soundLabel(strings, sound),
            color = if (selected) colors.ChipActiveText else colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            modifier = Modifier.weight(1f),
        )
        PreviewButton(enabled = true, onClick = onPreview)
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(18.dp))
        }
    }
}

/** Localized category label. */
private fun categoryLabel(strings: AppStrings, category: SoundEffectCategory): String = when (category) {
    SoundEffectCategory.BREAK_REMINDER -> strings.soundEffectsBreakReminder
    SoundEffectCategory.SCHEDULE_REMINDER -> strings.soundEffectsScheduleReminder
    SoundEffectCategory.SHORTS_LIMIT_WARNING -> strings.soundEffectsLimitWarning
    SoundEffectCategory.SHORTS_LIMIT_REACHED -> strings.soundEffectsLimitReached
    SoundEffectCategory.BREAK_START -> strings.soundEffectsBreakStart
    SoundEffectCategory.BREAK_END -> strings.soundEffectsBreakEnd
}

/** Localized sound-library label. */
private fun soundLabel(strings: AppStrings, sound: AppSound): String = when (sound) {
    AppSound.DEFAULT -> strings.appSoundDefault
    AppSound.GENTLE_CHIME -> strings.appSoundGentleChime
    AppSound.SOFT_BELL -> strings.appSoundSoftBell
    AppSound.CALM_TONE -> strings.appSoundCalmTone
    AppSound.FOCUS_TONE -> strings.appSoundFocusTone
    AppSound.WARNING_PULSE -> strings.appSoundWarningPulse
    AppSound.LIMIT_ALERT -> strings.appSoundLimitAlert
    AppSound.SUCCESS_CHIME -> strings.appSoundSuccessChime
}

/** Per-category icon — study/alert themed, matching the category meaning. */
private fun categoryIcon(category: SoundEffectCategory): ImageVector = when (category) {
    SoundEffectCategory.BREAK_REMINDER -> Icons.Filled.LocalCafe
    SoundEffectCategory.SCHEDULE_REMINDER -> Icons.Filled.Alarm
    SoundEffectCategory.SHORTS_LIMIT_WARNING -> Icons.Filled.WarningAmber
    SoundEffectCategory.SHORTS_LIMIT_REACHED -> Icons.Filled.Block
    SoundEffectCategory.BREAK_START -> Icons.Filled.PlayArrow
    SoundEffectCategory.BREAK_END -> Icons.Filled.CheckCircle
}
