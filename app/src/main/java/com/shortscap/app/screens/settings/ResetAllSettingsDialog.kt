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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
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
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlinx.coroutines.delay

/**
 * Premium "Reset All Settings" confirmation dialog — deliberately NOT a
 * platform AlertDialog. Premium dark card (24dp radius, elevation, thin
 * border), dimmed scrim, scale + fade entrance, danger header, the full
 * confirmation message, and a single row of equal-width buttons (outlined
 * Cancel + filled red Reset).
 *
 * Tapping Reset only invokes [onReset]; the actual restore is delegated to
 * the centralized [com.shortscap.app.settings.SettingsManager] by the caller.
 * Account / profile / session data are never touched.
 */
@Composable
fun ResetAllSettingsDialog(
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(24.dp)

    // Dimmed scrim fades in over the screen.
    var scrimShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { scrimShown = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (scrimShown) 0.62f else 0f,
        animationSpec = tween(240),
        label = "resetDialogScrim",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim — tap outside to dismiss.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
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
                        // Header — danger icon + title.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(colors.Danger.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.RestartAlt,
                                    contentDescription = null,
                                    tint = colors.Danger,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                strings.settingsResetAll,
                                color = colors.TextPrimary,
                                style = ScTextStyles.H1.copy(fontSize = 20.sp),
                                textAlign = TextAlign.Center,
                            )
                        }

                        // Confirmation message — three paragraphs.
                        Text(
                            strings.resetDialogMessage,
                            color = colors.TextSecondary,
                            style = ScTextStyles.Body,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Buttons — two equal-width buttons in one row.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ResetDialogButton(
                                label = strings.cancel,
                                filled = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            ResetDialogButton(
                                label = strings.resetAction,
                                filled = true,
                                onClick = onReset,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Premium dialog button — outlined (Cancel) or filled red (Reset), soft press scale. */
@Composable
private fun ResetDialogButton(
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
        label = "resetDialogButtonScale",
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
                    Modifier.background(colors.Danger)
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
