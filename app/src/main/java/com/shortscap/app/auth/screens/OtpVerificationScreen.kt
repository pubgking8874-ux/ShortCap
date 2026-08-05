package com.shortscap.app.auth.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.R
import com.shortscap.app.auth.components.AuthBackButton
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.auth.components.AuthTextButton
import com.shortscap.app.auth.components.OtpInputRow
import com.shortscap.app.auth.theme.SuccessColor
import com.shortscap.app.theme.LocalScColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** UI phase of the OTP screen (verification is mocked locally on this screen). */
private enum class OtpPhase { IDLE, VERIFYING, SUCCESS }

/**
 * OTP Verification — shared by the Forgot Password (reset), Mobile Login, and
 * Create Account (email verify) flows. Mirrors the Sign In / Create Account
 * design language (logo, glows, heading, compact footer) and implements
 * professional OTP behavior: auto-focus, auto-advance, backspace navigation,
 * paste distribution, error shake + red borders, a verifying state, and a
 * success animation before handing off to the existing [onVerify] navigation.
 */
@Composable
fun OtpVerificationScreen(
    destination: String = "",
    onBack: () -> Unit,
    onVerify: (otp: String) -> Unit,
    onResend: () -> Unit
) {
    val otpValues = remember { mutableStateListOf("", "", "", "", "", "") }
    var secondsLeft by remember { mutableIntStateOf(30) }
    var phase by remember { mutableStateOf(OtpPhase.IDLE) }
    var hasError by remember { mutableStateOf(false) }
    val submittedOtp = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scColors = LocalScColors.current

    val canResend = secondsLeft <= 0
    val isComplete = otpValues.all { it.isNotBlank() }

    // Resend countdown.
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    // Success -> brief pause -> hand off to the existing navigation hook.
    LaunchedEffect(phase) {
        if (phase == OtpPhase.SUCCESS) {
            delay(900)
            onVerify(submittedOtp.value)
        }
    }

    // Hold the screen while verifying / on success so back can't interrupt the flow.
    BackHandler(enabled = phase == OtpPhase.VERIFYING || phase == OtpPhase.SUCCESS) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Same subtle brand glows as Sign In / Create Account.
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
            Spacer(Modifier.height(8.dp))

            // Logo — identical size and spacing to Sign In / Create Account.
            Image(
                painter = painterResource(R.drawable.logo_pic),
                contentDescription = "ShortsCap logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(106.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(12.dp))

            Text(
                "Verify OTP",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (destination.isNotBlank())
                    "We've sent a 6-digit verification code to\n${maskDestination(destination)}"
                else
                    "We've sent a 6-digit verification code to your registered contact.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))
            OtpInputRow(
                otpValues = otpValues,
                isError = hasError,
                onValueChange = { index, value ->
                    if (hasError) hasError = false
                    otpValues[index] = value
                }
            )

            AnimatedVisibility(
                visible = hasError,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
                exit = fadeOut(tween(150)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Invalid verification code.\nPlease try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Didn't receive the code? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canResend) {
                    AuthTextButton(
                        text = "Resend Code",
                        onClick = {
                            secondsLeft = 30
                            onResend()
                        },
                        strong = true
                    )
                } else {
                    Text(
                        "Resend in ${formatCountdown(secondsLeft)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            AuthPrimaryButton(
                text = "Verify",
                enabled = isComplete && phase == OtpPhase.IDLE,
                loading = phase == OtpPhase.VERIFYING,
                loadingLabel = "Verifying...",
                onClick = {
                    submittedOtp.value = otpValues.joinToString("")
                    phase = OtpPhase.VERIFYING
                    scope.launch {
                        // Mock verification — every 6-digit code succeeds. Replace this
                        // delay with a real API call; on failure set hasError = true and
                        // phase = OtpPhase.IDLE instead (the boxes are not cleared).
                        delay(1200)
                        phase = OtpPhase.SUCCESS
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
        }

        // Full-screen success state: animated checkmark, then auto-redirect.
        AnimatedVisibility(
            visible = phase == OtpPhase.SUCCESS,
            enter = fadeIn(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            SuccessVerificationOverlay()
        }
    }
}

/** Centered success state — animated checkmark + "Redirecting..." message. */
@Composable
private fun SuccessVerificationOverlay() {
    val scheme = MaterialTheme.colorScheme
    val checkScale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        checkScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        scaleX = checkScale.value
                        scaleY = checkScale.value
                    }
                    .clip(CircleShape)
                    .background(SuccessColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Verification Successful",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Redirecting...",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Masks a destination so the full email/phone is never exposed:
 *  "dhruv@gmail.com"  -> "dh***@gmail.com"
 *  "+91 9876543210"   -> "+91 ••••••3210"
 */
private fun maskDestination(destination: String): String {
    val trimmed = destination.trim()
    if (trimmed.isEmpty()) return trimmed
    return if ('@' in trimmed) maskEmail(trimmed) else maskPhone(trimmed)
}

private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 1) return email // nothing safe to mask
    val local = email.substring(0, at)
    val domain = email.substring(at)
    return local.take(2) + "*".repeat(local.length - 2) + domain
}

private fun maskPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    if (digits.length < 4) return phone
    val last4 = digits.takeLast(4)
    // Preserve any non-digit characters in order (e.g. "+91 ", "(", "-").
    val prefix = phone.filterNot { it.isDigit() }
    return prefix + "•".repeat(digits.length - 4) + last4
}

/** Formats seconds as mm:ss, e.g. 30 -> "00:30". */
private fun formatCountdown(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}
