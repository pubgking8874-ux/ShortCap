package com.shortscap.app.auth.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.R
import com.shortscap.app.auth.components.AuthBackButton
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.auth.components.AuthTextButton
import com.shortscap.app.auth.components.EmailSignInButton
import com.shortscap.app.auth.components.GoogleSignInButton
import com.shortscap.app.auth.components.OrDivider
import com.shortscap.app.theme.LocalScColors

/**
 * A supported country for mobile-number login.
 *
 * Extending the list is a one-line change: add a [PhoneCountry] entry to
 * [SupportedPhoneCountries] and the selector + validation pick it up
 * automatically (no other code needs touching).
 */
data class PhoneCountry(
    val name: String,
    val dialCode: String,
    val flag: String,
    val maxNumberDigits: Int
)

/** The initial country catalog — extend by appending entries. */
val SupportedPhoneCountries = listOf(
    PhoneCountry(name = "India", dialCode = "+91", flag = "🇮🇳", maxNumberDigits = 10),
    PhoneCountry(name = "USA", dialCode = "+1", flag = "🇺🇸", maxNumberDigits = 10),
    PhoneCountry(name = "UK", dialCode = "+44", flag = "🇬🇧", maxNumberDigits = 10),
    PhoneCountry(name = "Canada", dialCode = "+1", flag = "🇨🇦", maxNumberDigits = 10),
    PhoneCountry(name = "Australia", dialCode = "+61", flag = "🇦🇺", maxNumberDigits = 9),
    PhoneCountry(name = "UAE", dialCode = "+971", flag = "🇦🇪", maxNumberDigits = 9)
)

/**
 * Mobile Number login — a dedicated screen, not a replacement for Email
 * login. Mirrors [LoginScreen]'s design exactly (same logo, heading, field
 * styling, buttons and footer) and hands off to the shared
 * [OtpVerificationScreen] after "Send OTP".
 */
@Composable
fun MobileLoginScreen(
    onBack: () -> Unit,
    onSendOtp: (phoneNumber: String) -> Unit,
    onContinueWithEmail: () -> Unit,
    onContinueWithGoogle: () -> Unit = {},
    onCreateAccount: () -> Unit,
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {}
) {
    var selectedCountry by remember { mutableStateOf(SupportedPhoneCountries.first()) }
    var phoneNumber by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val scColors = LocalScColors.current
    val phoneValid =
        phoneNumber.length >= 7 && phoneNumber.length <= selectedCountry.maxNumberDigits

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Same subtle premium depth accents as the Login screen.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-150).dp)
                .size(360.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(scColors.Accent.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 90.dp, y = 130.dp)
                .size(300.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(scColors.Accent2.copy(alpha = 0.06f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            AuthBackButton(onClick = onBack, refined = true)
            Spacer(Modifier.height(16.dp))

            Image(
                painter = painterResource(R.drawable.logo_pic),
                contentDescription = "ShortsCap logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(106.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(20.dp))

            Text(
                "Welcome Back",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Continue your ShortsCap journey",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            PhoneInputRow(
                selectedCountry = selectedCountry,
                countries = SupportedPhoneCountries,
                onCountrySelected = { selectedCountry = it },
                phoneNumber = phoneNumber,
                onPhoneNumberChange = {
                    phoneNumber = it
                        .filter { char -> char in '0'..'9' }
                        .take(selectedCountry.maxNumberDigits)
                }
            )

            Spacer(Modifier.height(24.dp))

            AuthPrimaryButton(
                text = "Send OTP",
                enabled = phoneValid,
                loading = loading,
                gradient = true,
                onClick = {
                    loading = true
                    // Mock: UI-only, same pattern as the rest of the auth flow.
                    onSendOtp("${selectedCountry.dialCode} $phoneNumber")
                    loading = false
                }
            )

            Spacer(Modifier.height(12.dp))
            OrDivider()
            Spacer(Modifier.height(12.dp))
            EmailSignInButton(onClick = onContinueWithEmail)
            Spacer(Modifier.height(12.dp))
            GoogleSignInButton(onClick = onContinueWithGoogle)

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                AuthTextButton(text = "Create Account", onClick = onCreateAccount, strong = true)
            }
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Privacy Policy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onPrivacyClick)
                )
                Text(
                    "  •  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Terms of Service",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onTermsClick)
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * Single-row phone input: | Country Code | Mobile Number | — one bordered
 * box (50dp tall, 14dp corners, animated 1dp→2dp border on focus) split by
 * a vertical divider, exactly matching the compact auth fields' look.
 */
@Composable
private fun PhoneInputRow(
    selectedCountry: PhoneCountry,
    countries: List<PhoneCountry>,
    onCountrySelected: (PhoneCountry) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var countryMenuOpen by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) scheme.primary else scheme.outline,
        animationSpec = tween(150),
        label = "phoneFieldBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = tween(150),
        label = "phoneFieldBorderWidth"
    )
    val hintAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.7f,
        animationSpec = tween(150),
        label = "phoneFieldHintAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .drawWithContent {
                drawContent()
                val strokeW = borderWidth.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(strokeW / 2, strokeW / 2),
                    size = Size(size.width - strokeW, size.height - strokeW),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = strokeW)
                )
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Country selector (flag + dial code + caret). Tapping opens the picker.
            Box(
                modifier = Modifier
                    .widthIn(min = 116.dp)
                    .fillMaxHeight()
                    .clickable { countryMenuOpen = true },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 10.dp)
                ) {
                    Text(
                        selectedCountry.flag,
                        fontSize = 18.sp,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        selectedCountry.dialCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Select country",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = countryMenuOpen,
                    onDismissRequest = { countryMenuOpen = false }
                ) {
                    countries.forEach { country ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${country.flag}  ${country.name}  (${country.dialCode})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface
                                )
                            },
                            onClick = {
                                onCountrySelected(country)
                                countryMenuOpen = false
                            },
                            trailingIcon = if (country == selectedCountry) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            // Vertical divider separating country code from the number.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(vertical = 12.dp)
                    .background(scheme.outline.copy(alpha = 0.5f))
            )

            // Mobile number input.
            BasicTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (phoneNumber.isEmpty()) {
                            Text(
                                "Mobile Number",
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant.copy(alpha = hintAlpha),
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}
