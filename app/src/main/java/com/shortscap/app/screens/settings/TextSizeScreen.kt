package com.shortscap.app.screens.settings

import androidx.compose.runtime.Composable
import com.shortscap.app.appearance.TextSizeMode
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings

/**
 * Text Size — dedicated page with Small / Medium (Default) / Large options.
 *
 * Selecting an option updates the entire application's typography instantly
 * (a root `LocalDensity` font-scale override) — settings titles, dashboard
 * and home labels, web platform names, buttons, Help & Support, Notifications,
 * Permissions, Monitoring, legal documents and future pages. Only text size
 * changes; icons, cards and layouts remain unchanged. The choice is persisted
 * locally.
 */
@Composable
fun TextSizeScreen(
    textSizeMode: TextSizeMode,
    onTextSizeChange: (TextSizeMode) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    SizeOptionScreen(
        title = strings.appearanceTextSize,
        current = textSizeMode,
        options = TextSizeMode.entries,
        labelFor = { it.displayName(strings) },
        onSelect = onTextSizeChange,
        onBack = onBack,
    )
}

private fun TextSizeMode.displayName(strings: AppStrings): String = when (this) {
    TextSizeMode.SMALL -> strings.sizeSmall
    TextSizeMode.MEDIUM -> strings.sizeMediumDefault
    TextSizeMode.LARGE -> strings.sizeLarge
}
