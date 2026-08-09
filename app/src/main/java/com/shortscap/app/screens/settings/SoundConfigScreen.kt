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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScPremiumNavCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.sounds.LocalSound
import com.shortscap.app.sounds.LocalSoundPlayer
import com.shortscap.app.sounds.SoundEffectCategory
import com.shortscap.app.sounds.SoundFolderMap
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Sound configuration screen — opened from ANY sound option in Study Mode,
 * Monitoring or Notifications. This is the single screen shared by all nine
 * categories; its content is driven by what is actually bundled in the
 * category's folder under the app's "all_sounds" assets (a mirror of the
 * user's "Downloads/All sounds" structure):
 *
 *  - ONE file   → used automatically as the current/default sound (no list).
 *  - MULTIPLE   → a "Choose" list with preview ▶ per sound; selection is
 *                 persisted and restored when the user returns.
 *  - ZERO files → a clean "No sounds available" empty state.
 *
 * "Current" and "Add from your device" remain available in every state.
 * Preview plays the real bundled file via AssetManager and never triggers any
 * reminder / notification / Study Mode / Shorts-limit behavior. Scanning is
 * dynamic — no filenames are hardcoded, so files re-bundled from the source
 * folders appear automatically.
 */
@Composable
fun SoundConfigScreen(
    category: SoundEffectCategory,
    loadSounds: suspend (SoundEffectCategory) -> List<LocalSound>,
    selectedSoundId: (SoundEffectCategory) -> String?,
    onSelectSound: (SoundEffectCategory, String) -> Unit,
    onAddFromDevice: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    var sounds by remember(category) { mutableStateOf<List<LocalSound>?>(null) }
    var selectedId by remember(category) { mutableStateOf(selectedSoundId(category)) }
    var previewingId by remember { mutableStateOf<String?>(null) }

    // Bundled assets need no storage permission — load immediately on open.
    LaunchedEffect(category) {
        sounds = loadSounds(category)
    }

    // Stop any running preview when leaving this screen.
    DisposableEffect(Unit) {
        onDispose { LocalSoundPlayer.stop() }
    }

    val available = sounds.orEmpty()
    val activeId = available.firstOrNull { it.id == selectedId }?.id ?: available.firstOrNull()?.id
    val activeSound = available.firstOrNull { it.id == activeId }

    fun togglePreview(sound: LocalSound) {
        if (previewingId == sound.id) {
            LocalSoundPlayer.stop()
            previewingId = null
        } else {
            previewingId = sound.id
            sound.assetPath?.let { path ->
                LocalSoundPlayer.playAsset(context, path) { previewingId = null }
            } ?: sound.uri?.let { uri ->
                LocalSoundPlayer.play(context, uri) { previewingId = null }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = categoryLabel(strings, category), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- When this sound is used — one-line context. ----
            Text(
                categoryDescription(strings, category),
                color = colors.TextSecondary,
                style = ScTextStyles.Body.copy(fontSize = 13.sp),
                modifier = Modifier.padding(start = 4.dp),
            )

            when {
                // ---- No audio files in the category folder. ----
                available.isEmpty() -> NoSoundsCard(folder = SoundFolderMap.folderName(category))

                else -> {
                    // ---- Current sound (auto default when only one file). ----
                    CurrentSoundCard(
                        category = category,
                        soundName = activeSound?.displayName.orEmpty(),
                        isPlaying = activeSound != null && previewingId == activeSound.id,
                        onPreview = { activeSound?.let { togglePreview(it) } },
                    )

                    // ---- Multiple sounds → "Choose" list with preview. ----
                    if (available.size > 1) {
                        Text(
                            strings.soundChoose,
                            color = colors.TextSecondary,
                            style = ScTextStyles.SectionTitle,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            available.forEach { sound ->
                                LocalSoundRow(
                                    sound = sound,
                                    selected = sound.id == activeId,
                                    isPlaying = previewingId == sound.id,
                                    onPreview = { togglePreview(sound) },
                                    onSelect = {
                                        selectedId = sound.id
                                        onSelectSound(category, sound.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ---- Add from your device — kept in every state. ----
            ScPremiumNavCard(
                icon = Icons.Filled.MusicNote,
                title = strings.soundAddFromDevice,
                subtitle = strings.soundAddFromDeviceDesc,
                onClick = onAddFromDevice,
            )
        }
    }
}

/** Premium info-style card: the option's icon + current sound name + preview ▶. */
@Composable
private fun CurrentSoundCard(
    category: SoundEffectCategory,
    soundName: String,
    isPlaying: Boolean,
    onPreview: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
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
                categoryIcon(category),
                contentDescription = null,
                tint = colors.Accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                strings.soundCurrentLabel,
                color = colors.TextSecondary,
                style = ScTextStyles.Caption.copy(fontSize = 11.sp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                soundName,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Preview ▶ / Stop — plays ONLY the current sound file.
        PreviewIconButton(
            isPlaying = isPlaying,
            onClick = onPreview,
            contentDescription = strings.soundEffectsPreview,
        )
    }
}

/** One selectable local sound — name + ▶ preview + selected check. */
@Composable
private fun LocalSoundRow(
    sound: LocalSound,
    selected: Boolean,
    isPlaying: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
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
            sound.displayName,
            color = if (selected) colors.ChipActiveText else colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PreviewIconButton(
            isPlaying = isPlaying,
            onClick = onPreview,
            contentDescription = strings.soundEffectsPreview,
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(18.dp))
        }
    }
}

/** Circular ▶ / Stop button that previews the sound file. */
@Composable
private fun PreviewIconButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
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
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = contentDescription,
            tint = colors.Accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Empty state — the category folder currently has no supported audio files. */
@Composable
private fun NoSoundsCard(folder: String) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicOff,
                contentDescription = null,
                tint = colors.TextDisabled,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            strings.soundNoSounds,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            strings.soundNoSoundsDesc(folder),
            color = colors.TextSecondary,
            style = ScTextStyles.Caption,
            textAlign = TextAlign.Center,
        )
    }
}
