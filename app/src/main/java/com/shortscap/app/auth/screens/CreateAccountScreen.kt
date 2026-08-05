package com.shortscap.app.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shortscap.app.auth.components.AuthBackButton
import com.shortscap.app.auth.components.AuthPasswordField
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.auth.components.AuthTextButton
import com.shortscap.app.auth.components.AuthTextField
import com.shortscap.app.auth.components.PasswordStrengthIndicator
import com.shortscap.app.auth.components.TermsCheckboxRow
import com.shortscap.app.auth.components.evaluatePasswordStrength

@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onCreateAccount: (fullName: String, email: String, password: String) -> Unit,
    onSignIn: () -> Unit,
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {}
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }

    val strength = remember(password) { evaluatePasswordStrength(password) }
    val passwordsMatch by remember(password, confirmPassword) {
        derivedStateOf { confirmPassword.isEmpty() || confirmPassword == password }
    }
    val formValid = fullName.isNotBlank() && email.isNotBlank() &&
        password.isNotBlank() && confirmPassword == password && agreedToTerms

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        AuthBackButton(onClick = onBack)
        Spacer(Modifier.height(16.dp))

        Text(
            "Create Your Account",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Join ShortsCap and start tracking today",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        AuthTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = "Full Name",
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) }
        )
        Spacer(Modifier.height(14.dp))
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(14.dp))
        AuthPasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visible = passwordVisible,
            onToggleVisible = { passwordVisible = !passwordVisible }
        )
        Spacer(Modifier.height(6.dp))
        PasswordStrengthIndicator(strength = strength)

        Spacer(Modifier.height(14.dp))
        AuthPasswordField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm Password",
            visible = confirmVisible,
            onToggleVisible = { confirmVisible = !confirmVisible },
            isError = !passwordsMatch,
            supportingText = if (!passwordsMatch) "Passwords do not match" else null
        )

        Spacer(Modifier.height(16.dp))
        TermsCheckboxRow(
            checked = agreedToTerms,
            onCheckedChange = { agreedToTerms = it },
            onTermsClick = onTermsClick,
            onPrivacyClick = onPrivacyClick
        )

        Spacer(Modifier.height(18.dp))
        AuthPrimaryButton(
            text = "Create Account",
            enabled = formValid,
            onClick = { onCreateAccount(fullName, email, password) }
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AuthTextButton(text = "Sign In", onClick = onSignIn)
        }
        Spacer(Modifier.height(28.dp))
    }
}
