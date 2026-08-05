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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import com.shortscap.app.auth.components.OrDivider
import com.shortscap.app.auth.components.PasswordStrengthIndicator
import com.shortscap.app.auth.components.SocialLoginRow
import com.shortscap.app.auth.components.TermsCheckboxRow
import com.shortscap.app.auth.components.evaluatePasswordStrength
import com.shortscap.app.theme.LocalScColors

/**
 * Create Account — collects only Email + Password (personal details are
 * gathered later on [CompleteProfileScreen] after verification). Mirrors the
 * Sign In design language (logo, glows, heading, compact fields, footer).
 */
@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onCreateAccount: (email: String, password: String) -> Unit,
    onSignIn: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onMobileSignIn: () -> Unit = {},
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }

    val strength = remember(password) { evaluatePasswordStrength(password) }
    val formValid = email.isNotBlank() && password.isNotBlank() && agreedToTerms

    val scColors = LocalScColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Same subtle premium depth accents as the Sign In / Mobile Login screens.
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

            // Logo — identical size and spacing to the Sign In screen.
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
                "Create Your Account",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Create your account and start taking control of your digital habits.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            AuthTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(12.dp))
            AuthPasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                visible = passwordVisible,
                onToggleVisible = { passwordVisible = !passwordVisible }
            )
            Spacer(Modifier.height(6.dp))
            PasswordStrengthIndicator(strength = strength)

            Spacer(Modifier.height(12.dp))
            TermsCheckboxRow(
                checked = agreedToTerms,
                onCheckedChange = { agreedToTerms = it },
                onTermsClick = onTermsClick,
                onPrivacyClick = onPrivacyClick
            )

            Spacer(Modifier.height(16.dp))
            AuthPrimaryButton(
                text = "Create Account",
                enabled = formValid,
                onClick = { onCreateAccount(email, password) }
            )

            Spacer(Modifier.height(12.dp))
            OrDivider(text = "Sign up with")
            Spacer(Modifier.height(12.dp))
            SocialLoginRow(
                onGoogleClick = onGoogleSignIn,
                onPhoneClick = onMobileSignIn
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
            Spacer(Modifier.height(24.dp))
        }
    }
}
