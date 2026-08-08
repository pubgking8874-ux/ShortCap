package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.study.FocusCountry
import com.shortscap.app.study.FocusRecoveryMethod
import com.shortscap.app.study.FocusSupportedCountries
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlinx.coroutines.delay

/**
 * Focus Exit Passcode — Study Mode protection & recovery screens.
 *
 * These pages are a dedicated "Study Focus Protection & Recovery" system:
 * they deliberately look NOTHING like the Sign In / Sign Up / auth OTP
 * screens. They reuse the Study Mode visual identity (lock/focus/book icon,
 * clean card, ShortsCap dark theme, ScTextStyles) and every label comes from
 * the i18n catalog.
 *
 * Flow (routes in SettingsNavHost):
 *   Setup   → verify → (Forgot Passcode?) → Recover → Email | Mobile
 *           → OTP (6-digit, resend countdown, demo code in the mock) → Create new
 * The ONLY way to end an active Study Mode session early is a correct
 * Focus Exit Passcode ([FocusPasscodeVerifyScreen]); natural completion at
 * 00:00 never requires one.
 */

// =====================================================================
// Shared building blocks (study-themed, not auth-styled)
// =====================================================================

/** Page scaffold: top bar + scrollable content column. */
@Composable
private fun FocusPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalScColors.current
    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Hero block: lock tile + title + one-line explanation. */
@Composable
private fun FocusHero(icon: ImageVector, title: String, subtitle: String) {
    val colors = LocalScColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold.copy(fontSize = 17.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = colors.TextSecondary,
            style = ScTextStyles.Body,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Study-styled text field. With [isPassword] it shows a hidden value + a
 * Show/Hide eye icon (the toggle switches between hidden and visible).
 * No artificial maximum length is enforced anywhere in this system.
 */
@Composable
private fun FocusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean = false,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    autoFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    var visible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.CardHover, shape)
            .border(1.dp, if (isError) colors.Danger else colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = ScTextStyles.Body.copy(color = colors.TextPrimary),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.Accent),
            visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.TextDisabled, style = ScTextStyles.Body)
                }
                inner()
            },
        )
        if (isPassword) {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = colors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

/** Small inline error line (non-aggressive, theme colors). */
@Composable
private fun FocusError(text: String) {
    val colors = LocalScColors.current
    Text(text, color = colors.Danger, style = ScTextStyles.Caption)
}

// =====================================================================
// 1. First-time setup — Set Focus Exit Passcode
// =====================================================================

/**
 * First-time setup. Shown when no Focus Exit Passcode exists yet. There is
 * deliberately NO "Forgot Passcode?" here — the user has not created one.
 */
@Composable
fun FocusPasscodeSetupScreen(
    onSave: (String) -> Boolean,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var passcode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FocusPage(title = strings.focusPasscodeSetupTitle, onBack = onBack) {
        FocusHero(icon = Icons.Filled.Lock, title = strings.focusPasscodeSetupTitle, subtitle = strings.focusPasscodeSetupDesc)

        FocusTextField(
            value = passcode,
            onValueChange = { passcode = it; error = null },
            placeholder = strings.focusPasscodeSetupFieldLabel,
            isPassword = true,
            isError = error != null,
            autoFocus = true,
        )
        if (error != null) FocusError(error!!)

        Spacer(Modifier.height(2.dp))
        ScButton(
            label = strings.focusPasscodeSetupSave,
            variant = ScButtonVariant.PRIMARY,
            onClick = {
                if (passcode.length < 8) error = strings.focusPasscodeTooShort
                else onSave(passcode) // success toast + return handled by the NavHost
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// =====================================================================
// 2. Verification — Enter Focus Exit Passcode (gated session end)
// =====================================================================

/**
 * The ONLY way to manually end an active Study Mode session. Incorrect
 * entries keep Study Mode fully active (countdown + restrictions untouched)
 * and simply show a calm error so the user can try again.
 */
@Composable
fun FocusPasscodeVerifyScreen(
    sessionActive: Boolean,
    onVerify: (String) -> Boolean,
    onForgot: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalScColors.current
    var passcode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FocusPage(title = strings.focusPasscodeVerifyTitle, onBack = onBack) {
        FocusHero(icon = Icons.Filled.Lock, title = strings.focusPasscodeVerifyTitle, subtitle = strings.focusPasscodeVerifyDesc)

        FocusTextField(
            value = passcode,
            onValueChange = { passcode = it; error = null },
            placeholder = strings.focusPasscodeVerifyPlaceholder,
            isPassword = true,
            isError = error != null,
            autoFocus = true,
        )
        if (error != null) FocusError(error!!)

        Spacer(Modifier.height(2.dp))
        ScButton(
            label = if (sessionActive) strings.focusPasscodeVerifyButton else strings.focusPasscodeVerifyOnly,
            variant = ScButtonVariant.PRIMARY,
            onClick = {
                if (!onVerify(passcode)) {
                    error = strings.focusPasscodeIncorrect
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // "Forgot Passcode?" — lives ONLY on this verification screen.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onForgot) {
                Text(strings.focusPasscodeForgot, color = colors.TextSecondary)
            }
        }
    }
}

// =====================================================================
// 3. Recovery method — Recover Focus Exit Passcode
// =====================================================================

/** Step 1 of recovery: choose Email OR Mobile (two separate cards). */
@Composable
fun FocusPasscodeRecoverScreen(
    onChooseEmail: () -> Unit,
    onChooseMobile: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    FocusPage(title = strings.focusPasscodeRecoverTitle, onBack = onBack) {
        FocusHero(icon = Icons.Filled.Lock, title = strings.focusPasscodeRecoverTitle, subtitle = strings.focusPasscodeRecoverDesc)

        RecoveryMethodCard(
            icon = Icons.Filled.Email,
            label = strings.focusPasscodeRecoverEmail,
            onClick = onChooseEmail,
        )
        RecoveryMethodCard(
            icon = Icons.Filled.Smartphone,
            label = strings.focusPasscodeRecoverMobile,
            onClick = onChooseMobile,
        )
    }
}

@Composable
private fun RecoveryMethodCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.Accent, modifier = Modifier.size(24.dp))
        }
        Text(label, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp))
    }
}

// =====================================================================
// 4 & 5. Email / Mobile verification (send code → OTP page)
// =====================================================================

@Composable
fun FocusPasscodeEmailScreen(
    onSendCode: (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    FocusPage(title = strings.focusPasscodeEmailTitle, onBack = onBack) {
        FocusHero(icon = Icons.Filled.Email, title = strings.focusPasscodeEmailTitle, subtitle = strings.focusPasscodeRecoverDesc)

        FocusTextField(
            value = email,
            onValueChange = { email = it; error = null },
            placeholder = strings.focusPasscodeEmailPlaceholder,
            keyboardType = KeyboardType.Email,
            isError = error != null,
            autoFocus = true,
        )
        if (error != null) FocusError(error!!)

        Spacer(Modifier.height(2.dp))
        ScButton(
            label = strings.focusPasscodeEmailSend,
            variant = ScButtonVariant.PRIMARY,
            onClick = {
                if (!emailPattern.matches(email.trim())) {
                    error = strings.focusPasscodeEmailInvalid
                } else {
                    onSendCode(email.trim())
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun FocusPasscodeMobileScreen(
    onSendCode: (FocusCountry, String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalScColors.current
    var country by remember { mutableStateOf(FocusSupportedCountries.first()) }
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var countryMenuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    FocusPage(title = strings.focusPasscodeMobileTitle, onBack = onBack) {
        FocusHero(icon = Icons.Filled.Smartphone, title = strings.focusPasscodeMobileTitle, subtitle = strings.focusPasscodeRecoverDesc)

        // Country-code selector (compact row, mirrors the auth mobile catalog).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.CardHover, shape)
                .border(1.dp, colors.Divider, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    countryMenuOpen = true
                }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${country.flag} ${country.name} (${country.dialCode})", color = colors.TextPrimary, style = ScTextStyles.Body, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(20.dp))
            DropdownMenu(expanded = countryMenuOpen, onDismissRequest = { countryMenuOpen = false }) {
                FocusSupportedCountries.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.flag} ${c.name} (${c.dialCode})", color = colors.TextPrimary) },
                        onClick = { country = c; countryMenuOpen = false },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Read-only dial-code box (not an editable-looking field).
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .clip(shape)
                    .background(colors.CardHover, shape)
                    .border(1.dp, colors.Divider, shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(country.dialCode, color = colors.TextPrimary, style = ScTextStyles.Body)
            }
            FocusTextField(
                value = number,
                onValueChange = {
                    number = it.filter { ch -> ch.isDigit() }.take(country.maxNumberDigits)
                    error = null
                },
                placeholder = strings.focusPasscodeMobilePlaceholder,
                keyboardType = KeyboardType.Number,
                isError = error != null,
                autoFocus = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (error != null) FocusError(error!!)

        Spacer(Modifier.height(2.dp))
        ScButton(
            label = strings.focusPasscodeMobileSend,
            variant = ScButtonVariant.PRIMARY,
            onClick = {
                if (number.length < 7) {
                    error = strings.focusPasscodeMobileInvalid
                } else {
                    onSendCode(country, number)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// =====================================================================
// 6. OTP verification — Verify Your Code
// =====================================================================

/**
 * Dedicated 6-digit code page with resend countdown. Clearly states whether
 * the code went to Email or Mobile (no sensitive data displayed). In the
 * local mock the generated code is shown as a subtle "Demo code" line —
 * removed automatically once the backend sends the code itself.
 */
@Composable
fun FocusPasscodeOtpScreen(
    method: FocusRecoveryMethod,
    demoCode: String?,
    contactMasked: String?,
    onVerify: (String) -> Boolean,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalScColors.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var resendKey by remember { mutableStateOf(0) }
    var secondsLeft by remember { mutableStateOf(60) }

    // Resend countdown — restarts on every resend tap.
    LaunchedEffect(resendKey) {
        secondsLeft = 60
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    FocusPage(title = strings.focusPasscodeOtpTitle, onBack = onBack) {
        FocusHero(
            icon = Icons.Filled.Lock,
            title = strings.focusPasscodeOtpTitle,
            subtitle = if (method == FocusRecoveryMethod.EMAIL) strings.focusPasscodeOtpEmailSent else strings.focusPasscodeOtpMobileSent,
        )
        // Where the code went, safely masked ("j••••@gmail.com").
        if (contactMasked != null) {
            Text(
                contactMasked,
                color = colors.TextSecondary,
                style = ScTextStyles.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Large centered 6-digit input.
        BasicTextField(
            value = code,
            onValueChange = {
                code = it.filter { ch -> ch.isDigit() }.take(6)
                error = null
            },
            textStyle = ScTextStyles.H1.copy(color = colors.TextPrimary, textAlign = TextAlign.Center, letterSpacing = 10.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.CardHover)
                .border(1.dp, if (error != null) colors.Danger else colors.Divider, RoundedCornerShape(14.dp))
                .padding(vertical = 16.dp, horizontal = 16.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (code.isEmpty()) {
                        Text(strings.focusPasscodeOtpEnterLabel, color = colors.TextDisabled, style = ScTextStyles.Body)
                    }
                    inner()
                }
            },
        )
        if (error != null) FocusError(error!!)

        Spacer(Modifier.height(2.dp))
        ScButton(
            label = strings.focusPasscodeOtpVerify,
            variant = ScButtonVariant.PRIMARY,
            enabled = code.length == 6,
            onClick = {
                if (!onVerify(code)) {
                    error = strings.focusPasscodeOtpIncorrect
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Resend + countdown.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (secondsLeft > 0) {
                Text(
                    strings.focusPasscodeOtpResendIn(secondsLeft),
                    color = colors.TextSecondary,
                    style = ScTextStyles.Caption,
                )
            } else {
                TextButton(onClick = { onResend(); resendKey++ }) {
                    Text(strings.focusPasscodeOtpResend, color = colors.Accent)
                }
            }
        }

        // Development-only line for the LOCAL mock OTP (no backend yet) —
        // replaced by a real SMS/email code when the backend connects.
        if (demoCode != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                strings.focusPasscodeOtpDemo(demoCode),
                color = colors.TextSecondary,
                style = ScTextStyles.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// =====================================================================
// 7. Create New Focus Exit Passcode (after successful OTP)
// =====================================================================

/** New + Confirm passcode with eye toggles; both must match (min 8 chars). */
@Composable
fun FocusPasscodeCreateScreen(
    onSave: (String) -> Boolean,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var passcode by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FocusPage(title = strings.focusPasscodeCreateTitle, onBack = onBack) {
        FocusHero(icon = Icons.Filled.Lock, title = strings.focusPasscodeCreateTitle, subtitle = strings.focusPasscodeRecoverDesc)

        FocusTextField(
            value = passcode,
            onValueChange = { passcode = it; error = null },
            placeholder = strings.focusPasscodeNewLabel,
            isPassword = true,
            autoFocus = true,
        )
        FocusTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            placeholder = strings.focusPasscodeConfirmLabel,
            isPassword = true,
            isError = error != null,
        )
        if (error != null) FocusError(error!!)

        Spacer(Modifier.height(2.dp))
        ScButton(
            label = strings.focusPasscodeCreateSave,
            variant = ScButtonVariant.PRIMARY,
            onClick = {
                when {
                    passcode.length < 8 -> error = strings.focusPasscodeTooShort
                    passcode != confirm -> error = strings.focusPasscodeMismatch
                    else -> onSave(passcode)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
