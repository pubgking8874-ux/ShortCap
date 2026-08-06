package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ThemeMode

/**
 * Theme — dedicated page with the Dark / Light / System Default selector.
 * Selecting any option applies the theme immediately (persisted via the
 * ViewModel); there is no Apply button.
 */
@Composable
fun ThemeScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.appearanceTheme, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                RadioOptionRow(
                    label = mode.displayName(strings),
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                )
            }
        }
    }
}

private fun ThemeMode.displayName(strings: AppStrings): String = when (this) {
    ThemeMode.DARK -> strings.appearanceDark
    ThemeMode.LIGHT -> strings.appearanceLight
    ThemeMode.SYSTEM -> strings.appearanceSystem
}
