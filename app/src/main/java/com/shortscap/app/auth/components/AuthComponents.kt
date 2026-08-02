package com.shortscap.app.auth.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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

/** Standard outlined text field with consistent 16dp rounding + focus animation. */
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = if (placeholder != null) {
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
            )
        } else {
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        },
        modifier = modifier.fillMaxWidth()
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = if (placeholder != null) {
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
            )
        } else {
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        },
        modifier = modifier.fillMaxWidth()
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
            OutlinedTextField(
                value = digit,
                onValueChange = { new ->
                    if (new.length <= 1 && new.all { it.isDigit() }) {
                        onValueChange(index, new)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier
                    .width(48.dp)
                    .height(56.dp)
            )
        }
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
