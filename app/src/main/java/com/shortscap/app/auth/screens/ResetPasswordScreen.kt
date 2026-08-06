package com.shortscap.app.auth.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shortscap.app.R
import com.shortscap.app.auth.components.AuthBackButton
import com.shortscap.app.auth.components.AuthPasswordField
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.auth.components.PasswordStrengthIndicator
import com.shortscap.app.auth.components.evaluatePasswordStrength
import com.shortscap.app.auth.theme.SuccessColor
import kotlinx.coroutines.delay

@Composable
fun ResetPasswordScreen(
    onBack: () -> Unit,
    onPasswordUpdated: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }

    val strength = remember(newPassword) { evaluatePasswordStrength(newPassword) }
    val passwordsMatch by remember(newPassword, confirmPassword) {
        derivedStateOf { confirmPassword.isEmpty() || confirmPassword == newPassword }
    }

    // Password is valid when it is not too weak (Medium or Strong).
    val passwordValid = strength.fraction >= 0.66f
    val newPasswordError = newPassword.isNotBlank() && strength.fraction < 0.33f
    val confirmError = confirmPassword.isNotBlank() && !passwordsMatch
    val formValid =
        newPassword.isNotBlank() && passwordValid && confirmPassword == newPassword

    // Auto-redirect to Sign In shortly after a successful update.
    LaunchedEffect(success) {
        if (success) {
            delay(1600)
            onPasswordUpdated()
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
        Spacer(Modifier.height(12.dp))

        // ShortsCap logo, consistent with the other authentication screens.
        Image(
            painter = painterResource(R.drawable.logo_pic),
            contentDescription = "ShortsCap logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(106.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Create New Password",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Create a strong password to keep your account secure.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            AuthPasswordField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = "New Password",
                visible = newVisible,
                onToggleVisible = { newVisible = !newVisible },
                // Lock icon on the left, Eye toggle on the right.
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                isError = newPasswordError,
                supportingText = if (newPasswordError) "Password is too weak" else null
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
                // Lock icon on the left, Eye toggle on the right.
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                isError = confirmError,
                supportingText = if (confirmError) "Passwords do not match" else null
            )

            Spacer(Modifier.height(24.dp))
            AuthPrimaryButton(
                text = "Update Password",
                enabled = formValid && !success,
                loading = success,
                loadingLabel = "Updating...",
                onClick = { success = true }
            )

            AnimatedVisibility(
                visible = success,
                enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(width = 8.dp, height = 1.dp))
                    Text(
                        "Password updated successfully!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SuccessColor
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}