package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.appearance.FontMode
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScFontFamilies
import com.shortscap.app.theme.ScTextStyles

/** Localized font-family name — also used as the Appearance row summary. */
fun FontMode.displayName(strings: AppStrings): String = when (this) {
    FontMode.SIMPLE -> strings.fontSimple
    FontMode.NUNITO -> strings.fontNunito
    FontMode.PATRICK_HAND -> strings.fontPatrickHand
    FontMode.ROBOTO -> strings.fontRoboto
    FontMode.TIMES_NEW_ROMAN -> strings.fontTimesNewRoman
}

/**
 * Font — the dedicated Font page (Settings → Appearance → Font).
 *
 * Five bundled font families in a clean vertical list. Each row shows a
 * live preview rendered in that ACTUAL font, the family name and a radio
 * selector. Tapping a row (or its radio) immediately selects, applies and
 * persists the font app-wide and shows a small "Font applied" toast — there
 * is NO Apply / Save / Cancel and no confirmation screen: the centralized
 * typography system (ScTextStyles → ScFonts) re-renders the whole app in the
 * new family instantly.
 *
 * Font and Language are independent preferences: changing one never affects
 * the other. Hindi / Urdu / Chinese glyphs fall back to Android's platform
 * fonts automatically (no empty boxes) — see FontSystem.kt.
 */
@Composable
fun FontScreen(
    currentFont: FontMode,
    onSelect: (FontMode) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.appearanceFont, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FontMode.entries.forEach { mode ->
                FontOptionRow(
                    mode = mode,
                    name = mode.displayName(strings),
                    family = ScFontFamilies.familyFor(mode),
                    selected = currentFont == mode,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

/** Font option row — name + live preview in the real font + radio selector. */
@Composable
private fun FontOptionRow(
    mode: FontMode,
    name: String,
    family: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.ChipActiveBg else colors.Card, shape)
            .border(1.dp, if (selected) colors.Accent.copy(alpha = 0.6f) else colors.Divider, shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            )
            Spacer(Modifier.height(3.dp))
            // Live specimen — rendered in the actual selected font.
            Text(
                text = strings.fontPreviewSample,
                fontFamily = family,
                color = colors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
