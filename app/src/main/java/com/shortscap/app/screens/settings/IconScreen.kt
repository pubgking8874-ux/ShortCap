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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconStyle
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Icons — the dedicated Icon Settings page (Settings → Appearance → Icons).
 *
 * The user picks an icon STYLE, not individual icons. Each style is a
 * premium selectable card with a live mini-preview (icon strip), name,
 * one-line description, and a check indicator on the selected card (subtle
 * ShortsCap-blue border + tint). A compact PREVIEW section at the bottom
 * reflects the currently selected (pending) style across the key categories
 * before anything is applied.
 *
 * Apply behavior is deliberate: browsing a card does NOT change the app —
 * only [onApply] persists the style, updates the global icon provider and
 * returns to the previous page (no app restart). Cancel discards the
 * pending selection. Apply is disabled while the pending style already
 * matches the active style.
 */
@Composable
fun IconScreen(
    currentStyle: IconStyle,
    onApply: (IconStyle) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var pending by remember { mutableStateOf(currentStyle) }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.appearanceIcons, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle(strings.iconStyleTitle)

            IconStyleCard(
                style = IconStyle.ORIGINAL,
                name = strings.iconStyleOriginal,
                description = strings.iconStyleOriginalDesc,
                previewKeys = StyleCardPreviewKeys,
                selected = pending == IconStyle.ORIGINAL,
                onClick = { pending = IconStyle.ORIGINAL },
            )
            IconStyleCard(
                style = IconStyle.VIBRANT,
                name = strings.iconStyleVibrant,
                description = strings.iconStyleVibrantDesc,
                previewKeys = StyleCardPreviewKeys,
                selected = pending == IconStyle.VIBRANT,
                onClick = { pending = IconStyle.VIBRANT },
            )

            SectionTitle(strings.iconStylePreview)
            PreviewCard(style = pending)
        }

        // Bottom action bar — Cancel discards, Apply persists + updates the
        // whole app through the centralized icon provider. navigationBarsPadding
        // lifts the buttons clear of the Android system navigation area (0 inset
        // on gesture nav, ~48dp on 3-button nav) so they never sit under it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScButton(
                label = strings.cancel,
                variant = ScButtonVariant.SECONDARY,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            ScButton(
                label = strings.apply,
                variant = ScButtonVariant.PRIMARY,
                enabled = pending != currentStyle,
                onClick = {
                    onApply(pending)
                    onBack()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Category icons shown inside each style card's mini preview strip. */
private val StyleCardPreviewKeys = listOf(
    IconKey.GENERAL,
    IconKey.MONITORING,
    IconKey.PERMISSIONS,
    IconKey.NOTIFICATIONS,
    IconKey.APPEARANCE,
)

/** Uppercased section heading, matching the app's section-title style. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = LocalScColors.current.TextSecondary,
        style = ScTextStyles.SectionTitle,
    )
}

/**
 * Premium selectable style card — mini icon strip + name + description +
 * check indicator. The selected card gets the ShortsCap-blue border/tint;
 * the whole card animates with a soft press-scale like the rest of the
 * ShortsCap premium surfaces.
 */
@Composable
private fun IconStyleCard(
    style: IconStyle,
    name: String,
    description: String,
    previewKeys: List<IconKey>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(22.dp)

    val bg by animateColorAsState(
        targetValue = if (pressed) colors.Accent.copy(alpha = 0.10f) else colors.Card,
        label = "iconStyleCardBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> colors.Accent.copy(alpha = 0.60f)
            pressed -> colors.Accent.copy(alpha = 0.40f)
            else -> colors.Divider
        },
        label = "iconStyleCardBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "iconStyleCardScale",
    )

    Column(
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
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mini preview strip — this style's icons in this style's colors.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                previewKeys.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.CardHover),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            IconTheme.icon(style, key),
                            contentDescription = null,
                            tint = IconTheme.tint(style, key, colors.Accent),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (selected) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.ChipActiveBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = strings.iconStyleSelected,
                        tint = colors.Accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(strings.iconStyleSelected, color = colors.ChipActiveText, style = ScTextStyles.Caption)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp))
        Spacer(Modifier.height(3.dp))
        Text(description, color = colors.TextSecondary, style = ScTextStyles.Body, maxLines = 2)
    }
}

/**
 * Compact PREVIEW section — the key categories exactly as they will look
 * under the pending [style]. Updates live as the user browses cards, so the
 * style can be understood before it is applied.
 */
@Composable
private fun PreviewCard(style: IconStyle) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val previewItems = listOf(
        IconKey.GENERAL to strings.settingsGeneral,
        IconKey.MONITORING to strings.settingsMonitoring,
        IconKey.PERMISSIONS to strings.settingsPermissions,
        IconKey.NOTIFICATIONS to strings.settingsNotifications,
        IconKey.DATA_BACKUP to strings.settingsDataBackup,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.Card, RoundedCornerShape(22.dp))
            .border(1.dp, colors.Divider, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        previewItems.forEach { (key, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(colors.CardHover),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        IconTheme.icon(style, key),
                        contentDescription = label,
                        tint = IconTheme.tint(style, key, colors.Accent),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    label,
                    color = colors.TextSecondary,
                    style = ScTextStyles.Caption.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
