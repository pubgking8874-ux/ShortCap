package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.sounds.SoundEffectCategory
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * "Add from Device" — the custom-audio destination for one sound option.
 *
 * FRONTEND ONLY: this is a placeholder navigation screen for the flow that
 * will eventually open the device's media/file picker. No audio importing,
 * file processing, storage, playback or permission logic lives here yet.
 * The screen communicates the intent with a clean empty state + a
 * non-functional "Choose File" action marked Coming Soon.
 */
@Composable
fun AddCustomSoundScreen(
    category: SoundEffectCategory,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.soundAddCustomTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.Card)
                    .border(1.dp, colors.Divider, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = colors.Accent,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                strings.soundAddCustomEmptyTitle,
                color = colors.TextPrimary,
                style = ScTextStyles.H1.copy(fontSize = 20.sp),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                strings.soundAddCustomEmptyDesc,
                color = colors.TextSecondary,
                style = ScTextStyles.Body,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            // Placeholder primary action — will open the media/file picker.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.CardHover)
                    .border(1.dp, colors.Divider, RoundedCornerShape(999.dp))
                    .alpha(0.6f)
                    .padding(horizontal = 26.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.soundChooseFile,
                    color = colors.TextPrimary,
                    style = ScTextStyles.ButtonLabel,
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.ChipActiveBg)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.comingSoon,
                    color = colors.ChipActiveText,
                    style = ScTextStyles.Caption.copy(fontSize = 11.sp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                strings.soundAddCustomDesc,
                color = colors.TextDisabled,
                style = ScTextStyles.Caption,
                textAlign = TextAlign.Center,
            )
        }
    }
}
