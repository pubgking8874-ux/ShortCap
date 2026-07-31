package com.shortscap.app.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shortscap.app.auth.components.AuthBackButton
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.auth.components.AuthTextButton
import com.shortscap.app.auth.components.OtpInputRow
import kotlinx.coroutines.delay

@Composable
fun OtpVerificationScreen(
    email: String = "",
    onBack: () -> Unit,
    onVerify: (otp: String) -> Unit,
    onResend: () -> Unit
) {
    val otpValues = remember { mutableStateListOf("", "", "", "", "", "") }
    var secondsLeft by remember { mutableIntStateOf(30) }
    var loading by remember { mutableStateOf(false) }
    val canResend = secondsLeft <= 0

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        AuthBackButton(onClick = onBack)
        Spacer(Modifier.height(28.dp))

        Text(
            "Verify OTP",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (email.isNotBlank())
                "Enter the 6-digit verification code sent to $email"
            else
                "Enter the 6-digit verification code.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(36.dp))
        OtpInputRow(
            otpValues = otpValues,
            onValueChange = { index, value -> otpValues[index] = value }
        )

        Spacer(Modifier.height(20.dp))
        Row {
            if (canResend) {
                AuthTextButton(
                    text = "Resend OTP",
                    onClick = {
                        secondsLeft = 30
                        onResend()
                    }
                )
            } else {
                Text(
                    "Resend OTP in 00:${secondsLeft.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        AuthPrimaryButton(
            text = "Verify",
            enabled = otpValues.all { it.isNotBlank() },
            loading = loading,
            onClick = {
                loading = true
                onVerify(otpValues.joinToString(""))
                loading = false
            }
        )
    }
}
