package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScPremiumInfoCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.theme.ThemeMode

/**
 * Appearance — dedicated screen holding the Theme selector (Dark / Light /
 * System Default). Same Material 3 radio rows and behavior as before, just
 * relocated from the old inline Settings expansion to its own page.
 */
@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.appearanceTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScPremiumInfoCard(
                icon = Icons.Filled.Palette,
                title = strings.appearanceTheme,
                subtitle = strings.appearanceThemeDesc,
                modifier = Modifier.fillMaxWidth(),
            )
            ThemeSelector(strings = strings, themeMode = themeMode, onThemeModeChange = onThemeModeChange)
        }
    }
}

/**
 * Mirrors the Appearance > Theme selector: Dark / Light / System Default.
 * Material 3 radio rows — exactly one selected at a time, with a clear
 * visual indicator (highlight + radio dot). Persisted via the ViewModel.
 */
@Composable
private fun ThemeSelector(
    strings: com.shortscap.app.i18n.AppStrings,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val colors = LocalScColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { mode ->
            val selected = themeMode == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) colors.ChipActiveBg else colors.Card)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onThemeModeChange(mode) },
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(mode.displayName(strings), color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
                RadioButton(
                    selected = selected,
                    onClick = null,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

private fun ThemeMode.displayName(strings: com.shortscap.app.i18n.AppStrings): String = when (this) {
    ThemeMode.DARK -> strings.appearanceDark
    ThemeMode.LIGHT -> strings.appearanceLight
    ThemeMode.SYSTEM -> strings.appearanceSystem
}
