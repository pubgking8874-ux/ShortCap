package com.shortscap.app.auth.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.shortscap.app.R
import com.shortscap.app.auth.theme.GradientEnd
import com.shortscap.app.auth.theme.GradientStart
import com.shortscap.app.auth.theme.SuccessColor
import com.shortscap.app.auth.theme.WarningColor
import com.shortscap.app.theme.LocalScColors

/** Filled, full-width primary CTA — [gradient] enables the premium ShortsCap brand look. */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    gradient: Boolean = false
) {
    val scColors = LocalScColors.current
    val shape = RoundedCornerShape(16.dp)
    val isActive = enabled && !loading
    // Theme-adaptive "soft dark gray" disabled treatment (reads gray in both themes).
    val disabledSurface = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val disabledContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val styledModifier = if (gradient) {
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                brush = if (isActive) {
                    Brush.linearGradient(listOf(scColors.Accent, scColors.Accent2))
                } else {
                    SolidColor(disabledSurface)
                },
                shape = shape
            )
    } else {
        modifier
            .fillMaxWidth()
            .height(56.dp)
    }

    Button(
        onClick = onClick,
        enabled = isActive,
        modifier = styledModifier,
        shape = shape,
        colors = if (gradient) {
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = disabledContent
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        },
        elevation = if (gradient) {
            ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 2.dp,
                disabledElevation = 0.dp
            )
        } else {
            ButtonDefaults.buttonElevation()
        }
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Outlined secondary button, e.g. "Sign In" on Welcome screen. */
@Composable
fun AuthSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Low-emphasis text link, e.g. "Create Account" / "Forgot Password?". */
@Composable
fun AuthTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    strong: Boolean = false
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Compact auth text fields
//
// Material 3 hard-codes a 56dp minimum height on OutlinedTextField (TextFieldDefaults.MinHeight),
// so no contentPadding change can make the stock component shorter. These fields are hand-built on
// BasicTextField with the *exact* M3 1.3.0 look — same 14dp corners, same border colors/thickness,
// same floating-label animation (bodyLarge -> bodySmall lerp, 150ms), same border notch cutout,
// same leading/trailing icon alignment and colors, same error + supporting-text behavior — but at a
// compact 50dp height with ~20% tighter effective vertical padding. Text, hint and icons stay
// vertically centered, and the 50dp height keeps a comfortable Material touch target (>= 48dp).
// ---------------------------------------------------------------------------------------------

/** Height of every compact auth field (was 56dp in stock M3). */
private val CompactFieldHeight = 50.dp

/** Corner rounding for the compact auth fields (unchanged from the original 14dp). */
private val CompactFieldCornerRadius = 14.dp

/** M3 default animation duration for text field transitions. */
private const val FieldAnimationDurationMillis = 150

/** Standard compact outlined field used by every authentication input. */
@Composable
private fun CompactAuthOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Label float progress (focused or filled) — drives the label's position/size and the
    // placeholder fade, mirroring M3's transition.
    val labelProgress by animateFloatAsState(
        targetValue = if (isFocused || value.isNotEmpty()) 1f else 0f,
        animationSpec = tween(FieldAnimationDurationMillis),
        label = "compactAuthLabelProgress"
    )

    // Border: 1dp -> 2dp on focus, animated color (error > focused > unfocused). The
    // unfocused border honors the original alpha-0.8 treatment used when a placeholder is set.
    val targetBorderColor = when {
        isError -> scheme.error
        isFocused -> scheme.primary
        else -> if (placeholder != null) scheme.outline.copy(alpha = 0.8f) else scheme.outline
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(FieldAnimationDurationMillis),
        label = "compactAuthBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = tween(FieldAnimationDurationMillis),
        label = "compactAuthBorderWidth"
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isError -> scheme.error
            isFocused -> scheme.primary
            else -> scheme.onSurfaceVariant
        },
        animationSpec = tween(FieldAnimationDurationMillis),
        label = "compactAuthLabelColor"
    )

    val placeholderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(FieldAnimationDurationMillis),
        label = "compactAuthPlaceholderAlpha"
    )

    // Live label size drives the top-border notch cutout (M3's outlineCutout behavior).
    val labelSize = remember { mutableStateOf(Size.Zero) }

    // Label geometry: M3 lerps the label between bodyLarge (idle, centered) and bodySmall
    // (floating on the top border) while sliding it up. Reproduced with identical formulas.
    val labelIdleStyle = MaterialTheme.typography.bodyLarge
    val labelFloatingStyle = MaterialTheme.typography.bodySmall
    val animatedLabelStyle = lerp(labelIdleStyle, labelFloatingStyle, labelProgress)
    val animatedLabelHeightPx = with(density) {
        if (animatedLabelStyle.lineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) {
            animatedLabelStyle.lineHeight.toPx()
        } else {
            24.dp.toPx()
        }
    }
    val fieldHeightPx = with(density) { CompactFieldHeight.toPx() }
    // Idle label sits vertically centered, shifted right of a leading icon ((48 - 12) + 16 = 52dp).
    val idleLabelXPx = with(density) { (if (leadingIcon != null) 52.dp else 16.dp).toPx() }
    val labelXPx = lerp(idleLabelXPx, with(density) { 16.dp.toPx() }, labelProgress)
    val labelYPx = lerp(
        (fieldHeightPx - animatedLabelHeightPx) / 2f,
        -animatedLabelHeightPx / 2f,
        labelProgress
    )

    // Horizontal insets replicate M3: 16dp, reduced by the icon gutter when an icon is present.
    val startTextPadding = if (leadingIcon != null) 4.dp else 16.dp
    val endTextPadding = if (trailingIcon != null) 4.dp else 16.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CompactFieldHeight)
                .drawWithContent {
                    drawContent()
                    val strokeW = borderWidth.toPx()
                    val corner = CompactFieldCornerRadius.toPx()
                    val drawBorder: DrawScope.() -> Unit = {
                        drawRoundRect(
                            color = borderColor,
                            topLeft = Offset(strokeW / 2, strokeW / 2),
                            size = Size(size.width - strokeW, size.height - strokeW),
                            cornerRadius = CornerRadius(corner),
                            style = Stroke(width = strokeW)
                        )
                    }
                    val labelW = labelSize.value.width
                    if (labelW > 0f) {
                        // Cut the top border around the floating label, exactly like M3's cutout.
                        val inner = 4.dp.toPx()
                        val left = 16.dp.toPx() - inner
                        val right = left + labelW + 2 * inner
                        val labelH = labelSize.value.height
                        clipRect(left, -labelH / 2, right, labelH / 2, ClipOp.Difference) {
                            drawBorder()
                        }
                    } else {
                        drawBorder()
                    }
                }
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterStart),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
                        leadingIcon()
                    }
                }
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxSize(),
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = startTextPadding, end = endTextPadding),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Only compose the placeholder once it is visible — an alpha-0 composable
                        // would still be read by TalkBack (M3 removes it for the same reason).
                        if (value.isEmpty() && placeholder != null && placeholderAlpha > 0f) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant.copy(alpha = placeholderAlpha),
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (label.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = labelXPx
                            translationY = labelYPx
                        }
                        .onSizeChanged {
                            labelSize.value = Size(it.width.toFloat(), it.height.toFloat())
                        }
                ) {
                    Text(
                        text = label,
                        style = animatedLabelStyle.copy(color = labelColor),
                        maxLines = 1
                    )
                }
            }

            if (trailingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (isError) scheme.error else scheme.onSurfaceVariant
                    ) {
                        trailingIcon()
                    }
                }
            }
        }

        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)
            )
        }
    }
}

/** Standard outlined text field with consistent 16dp rounding + focus animation (compact height). */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    placeholder: String? = null
) {
    CompactAuthOutlinedField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        keyboardType = keyboardType,
        isError = isError,
        supportingText = supportingText,
        singleLine = singleLine,
        placeholder = placeholder
    )
}

/** Password field with a show/hide trailing toggle, built on top of AuthTextField styling. */
@Composable
fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null
) {
    CompactAuthOutlinedField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        isError = isError,
        supportingText = supportingText,
        placeholder = placeholder,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        }
    )
}

enum class PasswordStrength(val label: String, val color: Color, val fraction: Float) {
    EMPTY("", Color.Transparent, 0f),
    WEAK("Weak", Color(0xFFE5484D), 0.33f),
    MEDIUM("Medium", WarningColor, 0.66f),
    STRONG("Strong", SuccessColor, 1f)
}

fun evaluatePasswordStrength(password: String): PasswordStrength = when {
    password.isEmpty() -> PasswordStrength.EMPTY
    password.length < 6 -> PasswordStrength.WEAK
    password.length < 10 ||
        !password.any { it.isDigit() } ||
        !password.any { it.isUpperCase() } -> PasswordStrength.MEDIUM
    else -> PasswordStrength.STRONG
}

/** Animated strength bar shown under password fields on Create Account / Reset Password. */
@Composable
fun PasswordStrengthIndicator(strength: PasswordStrength, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = strength != PasswordStrength.EMPTY, enter = fadeIn(), exit = fadeOut()) {
        Column(modifier = modifier.fillMaxWidth()) {
            val animatedWidthFraction by animateDpAsState(
                targetValue = (240.dp * strength.fraction),
                animationSpec = tween(300),
                label = "strengthWidth"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .width(animatedWidthFraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(strength.color)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = strength.label,
                style = MaterialTheme.typography.bodySmall,
                color = strength.color
            )
        }
    }
}

private val OtpBoxCornerRadius = 12.dp
private val OtpBoxHeight = 50.dp

/** Six-box OTP entry row. Purely local state driven, no verification logic. */
@Composable
fun OtpInputRow(
    otpValues: List<String>,
    onValueChange: (index: Int, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        otpValues.forEachIndexed { index, digit ->
            CompactOtpDigitBox(
                value = digit,
                onValueChange = { new ->
                    if (new.length <= 1 && new.all { it.isDigit() }) {
                        onValueChange(index, new)
                    }
                },
                modifier = Modifier
                    .width(48.dp)
                    .height(OtpBoxHeight)
            )
        }
    }
}

/** Single OTP digit box — compact 50dp, centered digit, same border behavior as before. */
@Composable
private fun CompactOtpDigitBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) scheme.primary else scheme.outline,
        animationSpec = tween(FieldAnimationDurationMillis),
        label = "compactOtpBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = tween(FieldAnimationDurationMillis),
        label = "compactOtpBorderWidth"
    )

    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            val strokeW = borderWidth.toPx()
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(strokeW / 2, strokeW / 2),
                size = Size(size.width - strokeW, size.height - strokeW),
                cornerRadius = CornerRadius(OtpBoxCornerRadius.toPx()),
                style = Stroke(width = strokeW)
            )
        },
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    innerTextField()
                }
            }
        )
    }
}

/** Google sign-in button — official multicolor G, polished alignment. */
@Composable
fun GoogleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Continue with Google",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Outlined secondary sign-in options (Email / Mobile) — visually identical to the Google button. */
@Composable
private fun OutlinedOptionButton(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** "Continue with Mobile Number" — modern smartphone icon, same style as the Google button. */
@Composable
fun MobileSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedOptionButton(
        text = "Continue with Mobile Number",
        onClick = onClick,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Filled.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

/** "Continue with Email" — same style as the Google button, used to jump back to Email login. */
@Composable
fun EmailSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedOptionButton(
        text = "Continue with Email",
        onClick = onClick,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

/** "OR" divider used between primary auth action and social sign-in. */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        HorizontalRule(Modifier.weight(1f))
        Text(
            "  OR  ",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        HorizontalRule(Modifier.weight(1f))
    }
}

@Composable
private fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}

/** Simple back-navigation top bar reused on Login / Forgot Password / etc. */
@Composable
fun AuthBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    refined: Boolean = false
) {
    if (refined) {
        IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Gradient app mark used on Splash / Welcome for a premium hero look. */
@Composable
fun BrandLogoMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "SC",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TermsCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )
        Row {
            Text("I agree to the ", style = MaterialTheme.typography.bodySmall)
            Text(
                "Terms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onTermsClick() }
            )
            Text(" & ", style = MaterialTheme.typography.bodySmall)
            Text(
                "Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onPrivacyClick() }
            )
        }
    }
}
