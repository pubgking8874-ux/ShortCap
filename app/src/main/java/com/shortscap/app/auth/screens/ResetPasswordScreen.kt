package com.shortscap.app.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shortscap.app.auth.components.AuthBackButton
import com.shortscap.app.auth.components.AuthPasswordField
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.auth.components.PasswordStrengthIndicator
import com.shortscap.app.auth.components.evaluatePasswordStrength

@Composable
fun ResetPasswordScreen(
    onBack: () -> Unit,
    onPasswordUpdated: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val strength = remember(newPassword) { evaluatePasswordStrength(newPassword) }
    val passwordsMatch by remember(newPassword, confirmPassword) {
        derivedStateOf { confirmPassword.isEmpty() || confirmPassword == newPassword }
    }
    val formValid = newPassword.isNotBlank() && confirmPassword == newPassword

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
            "Create New Password",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your new password must be different from previously used passwords.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
        AuthPasswordField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = "New Password",
            visible = newVisible,
            onToggleVisible = { newVisible = !newVisible }
        )
        Spacer(Modifier.height(8.dp))
        PasswordStrengthIndicator(strength = strength)

        Spacer(Modifier.height(16.dp))
        AuthPasswordField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm Password",
            visible = confirmVisible,
            onToggleVisible = { confirmVisible = !confirmVisible },
            isError = !passwordsMatch,
            supportingText = if (!passwordsMatch) "Passwords do not match" else null
        )

        Spacer(Modifier.height(28.dp))
        AuthPrimaryButton(
            text = "Update Password",
            enabled = formValid,
            loading = loading,
            onClick = {
                loading = true
                // UI-only: pretend the update succeeded, then bounce to Login.
                onPasswordUpdated()
                loading = false
            }
        )
    }
}
