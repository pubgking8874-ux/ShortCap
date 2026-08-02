package com.shortscap.app.auth.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.R
import com.shortscap.app.auth.components.AuthBackButton
import com.shortscap.app.auth.components.AuthPasswordField
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.auth.components.AuthTextButton
import com.shortscap.app.auth.components.AuthTextField
import com.shortscap.app.auth.components.GoogleSignInButton
import com.shortscap.app.auth.components.OrDivider
import com.shortscap.app.theme.LocalScColors

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onGoogleSignIn: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val scColors = LocalScColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle premium depth accents — barely-visible brand glows, no images.
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
            Spacer(Modifier.height(16.dp))
            AuthBackButton(onClick = onBack, refined = true)
            Spacer(Modifier.height(30.dp))

            Image(
                painter = painterResource(R.drawable.logo_pic),
                contentDescription = "ShortsCap logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(32.dp))

            Text(
                "Welcome Back",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Continue your digital wellbeing journey.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(36.dp))

            AuthTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "you@example.com",
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(16.dp))
            AuthPasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "Enter your password",
                visible = passwordVisible,
                onToggleVisible = { passwordVisible = !passwordVisible }
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = Color.White,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                    Text(
                        "Remember Me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AuthTextButton(text = "Forgot Password?", onClick = onForgotPassword)
            }

            Spacer(Modifier.height(14.dp))

            AuthPrimaryButton(
                text = "Sign In",
                loading = loading,
                enabled = email.isNotBlank() && password.isNotBlank(),
                gradient = true,
                onClick = {
                    loading = true
                    // Mock: UI-only, no real auth call. Simulate a brief loading state.
                    onSignIn(email, password)
                    loading = false
                }
            )

            Spacer(Modifier.height(24.dp))
            OrDivider()
            Spacer(Modifier.height(24.dp))
            GoogleSignInButton(onClick = onGoogleSignIn)

            Spacer(Modifier.height(30.dp))

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
            Spacer(Modifier.height(24.dp))
        }
    }
}
