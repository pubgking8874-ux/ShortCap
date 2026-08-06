package com.shortscap.app.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.AppLanguage
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** English display name of each language, used next to the native name. */
private fun AppStrings.englishName(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> languageEnglish
    AppLanguage.HINDI -> languageHindi
    AppLanguage.URDU -> languageUrdu
    AppLanguage.CHINESE -> languageChinese
    AppLanguage.SPANISH -> languageSpanish
}

/**
 * Language — full-screen picker (no dialog/sheet for the list itself).
 *
 * Each language shows its flag + native name (+ English name, or "(Default)"
 * for English); the current selection carries a checkmark and only one
 * language can be selected. Tapping a different language opens a confirmation
 * dialog (Current / New, Cancel / Apply); Apply immediately switches the whole
 * logged-in experience and pops back to General.
 */
@Composable
fun LanguageScreen(
    currentLanguage: AppLanguage,
    onApplyLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var pending by remember { mutableStateOf<AppLanguage?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.languageTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppLanguage.entries.forEach { language ->
                val label = if (language == AppLanguage.ENGLISH) {
                    "${language.nativeName} (${strings.languageDefaultSuffix})"
                } else {
                    "${language.nativeName} (${strings.englishName(language)})"
                }
                LanguageRow(
                    flag = language.flag,
                    label = label,
                    selected = language == currentLanguage,
                    onClick = {
                        if (language != currentLanguage) pending = language
                    },
                )
            }
        }
    }

    pending?.let { newLanguage ->
        ChangeLanguageDialog(
            currentLanguage = currentLanguage,
            newLanguage = newLanguage,
            onDismiss = { pending = null },
            onApply = {
                onApplyLanguage(newLanguage)
                pending = null
                onBack()
            },
        )
    }
}

/** Premium selectable row — flag, name, checkmark on the current language. */
@Composable
private fun LanguageRow(
    flag: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(22.dp)

    val bg by animateColorAsState(
        targetValue = if (pressed) colors.Accent.copy(alpha = 0.10f) else colors.Card,
        label = "languageRowBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> colors.Accent.copy(alpha = 0.55f)
            pressed -> colors.Accent.copy(alpha = 0.40f)
            else -> colors.Divider
        },
        label = "languageRowBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "languageRowScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (pressed) 10.dp else 2.dp,
                shape = shape,
                clip = false,
            )
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(flag, fontSize = 30.sp, modifier = Modifier.size(40.dp), textAlign = TextAlign.Center)
        Text(
            label,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.Accent,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Spacer(Modifier.height(0.dp))
        }
    }
}
