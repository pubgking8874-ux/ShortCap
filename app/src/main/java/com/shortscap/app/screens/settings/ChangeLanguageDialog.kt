package com.shortscap.app.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shortscap.app.i18n.AppLanguage
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlinx.coroutines.delay

/**
 * Custom ShortsCap language-change confirmation dialog — deliberately NOT a
 * platform AlertDialog. Premium dark card (24dp radius, elevation, thin
 * border), dimmed scrim, scale + fade entrance, globe header, Current → New
 * comparison card, info note, and a single row of equal-width buttons
 * (outlined Cancel + filled Apply).
 *
 * Layout is strictly vertical: 24dp padding, equal spacing between sections,
 * capped width (max 460dp) and height (90% of window) with internal scrolling,
 * so nothing overlaps or clips on any screen size.
 *
 * On Apply the dialog swaps to a staged loading state — "Applying language…"
 * then "Switching to <language>…" — and only then invokes [onApply], so the
 * app-level refresh overlay takes over smoothly (no abrupt screen reload).
 */
@Composable
fun ChangeLanguageDialog(
    currentLanguage: AppLanguage,
    newLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(24.dp)

    var applying by remember { mutableStateOf(false) }
    var showSwitching by remember { mutableStateOf(false) }

    // Staged loading: "Applying language…" → "Switching to <language>…" → apply.
    LaunchedEffect(applying) {
        if (applying) {
            delay(400)
            showSwitching = true
            delay(450)
            onApply()
        }
    }

    // Dimmed scrim fades in over the screen.
    var scrimShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { scrimShown = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (scrimShown) 0.62f else 0f,
        animationSpec = tween(240),
        label = "languageDialogScrim",
    )

    Dialog(
        onDismissRequest = { if (!applying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim — tap outside to dismiss (blocked while applying).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (!applying) onDismiss() },
                    ),
            )

            // Card — centered, capped to 90% height with internal scrolling.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(240)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 460.dp)
                            .heightIn(max = maxHeight)
                            .verticalScroll(rememberScrollState())
                            .shadow(28.dp, shape, clip = false)
                            .clip(shape)
                            .background(colors.Card, shape)
                            .border(1.dp, colors.Divider, shape)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // Form and loading are mutually exclusive — never stacked,
                        // so nothing can overlap during the switch.
                        if (applying) {
                            DialogLoadingContent(
                                showSwitching = showSwitching,
                                switchingLabel = strings.switchingTo(newLanguage.nativeName),
                            )
                        } else {
                            DialogFormContent(
                                currentLanguage = currentLanguage,
                                newLanguage = newLanguage,
                                onCancel = onDismiss,
                                onApply = { applying = true },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Confirmation form: globe header, comparison card, info note, buttons. */
@Composable
private fun DialogFormContent(
    currentLanguage: AppLanguage,
    newLanguage: AppLanguage,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header — globe icon + title.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.Accent.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🌐", fontSize = 30.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                strings.languageDialogTitle,
                color = colors.TextPrimary,
                style = ScTextStyles.H1.copy(fontSize = 20.sp),
                textAlign = TextAlign.Center,
            )
        }

        // Current → New comparison card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.Bg2, RoundedCornerShape(16.dp))
                .border(1.dp, colors.Divider, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LanguageCompareRow(
                label = strings.languageCurrentLabel,
                language = currentLanguage,
                highlighted = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.height(1.dp).weight(1f).background(colors.Divider))
                Spacer(Modifier.width(12.dp))
                Text("↓", color = colors.Accent, fontSize = 16.sp, style = ScTextStyles.BodySemiBold)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.height(1.dp).weight(1f).background(colors.Divider))
            }
            LanguageCompareRow(
                label = strings.languageNewLabel,
                language = newLanguage,
                highlighted = true,
            )
        }

        // Info note.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.Accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .border(1.dp, colors.Accent.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                strings.languageInfoNote,
                color = colors.TextSecondary,
                style = ScTextStyles.Caption.copy(fontSize = 12.sp),
                modifier = Modifier.weight(1f),
            )
        }

        // Buttons — two equal-width buttons in one horizontal row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DialogButton(
                label = strings.cancel,
                filled = false,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            DialogButton(
                label = strings.apply,
                filled = true,
                onClick = onApply,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Flag + native name row inside the comparison card. */
@Composable
private fun LanguageCompareRow(
    label: String,
    language: AppLanguage,
    highlighted: Boolean,
) {
    val colors = LocalScColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = colors.TextDisabled,
            style = ScTextStyles.Caption.copy(fontSize = 11.sp),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(language.flag, fontSize = 24.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.width(10.dp))
            Text(
                language.nativeName,
                color = if (highlighted) colors.Accent else colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            )
        }
    }
}

/** Staged loading state shown after Apply is pressed. */
@Composable
private fun DialogLoadingContent(
    showSwitching: Boolean,
    switchingLabel: String,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = colors.Accent,
            strokeWidth = 3.dp,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            strings.languageApplying,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            textAlign = TextAlign.Center,
        )
        AnimatedVisibility(visible = showSwitching) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                Text(
                    switchingLabel,
                    color = colors.TextSecondary,
                    style = ScTextStyles.Caption,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Premium dialog button — filled (primary) or outlined (secondary), soft press scale. */
@Composable
private fun DialogButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "dialogButtonScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(50.dp)
            .shadow(elevation = if (filled) 8.dp else 0.dp, shape = shape, clip = false)
            .clip(shape)
            .then(
                if (filled) {
                    Modifier.background(colors.Accent)
                } else {
                    Modifier
                        .background(Color.Transparent)
                        .border(1.dp, colors.Divider, shape)
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) Color.White else colors.TextSecondary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 14.sp),
        )
    }
}
