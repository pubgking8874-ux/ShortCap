package com.shortscap.app.auth.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.R
import com.shortscap.app.auth.components.AuthBackButton
import com.shortscap.app.auth.components.AuthPickerField
import com.shortscap.app.auth.components.AuthPrimaryButton
import com.shortscap.app.auth.components.AuthTextField
import com.shortscap.app.theme.LocalScColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class GenderOption(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    PREFER_NOT_TO_SAY("Prefer not to say")
}

/** Formats a UTC millis timestamp as DD/MM/YYYY in the device's time zone. */
private fun formatDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val dd = date.dayOfMonth.toString().padStart(2, '0')
    val mm = date.monthValue.toString().padStart(2, '0')
    return "$dd/$mm/${date.year}"
}

/**
 * Complete Profile — a dedicated page shared by every sign-in method. After
 * authentication (Email / Google / Mobile), the user supplies their Name,
 * Gender and Date of Birth here before reaching the Dashboard. Reuses the
 * auth design language end-to-end (logo, fields, buttons, footer).
 *
 * [initialName] lets Google login pre-fill the display name (still editable);
 * Email and Mobile flows pass nothing and leave the field empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(
    onBack: () -> Unit,
    onContinue: (name: String, gender: String, dateOfBirth: String) -> Unit,
    initialName: String = "",
    onSkip: () -> Unit = {},
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var gender by remember { mutableStateOf<String?>(null) }
    var dobMillis by remember { mutableStateOf<Long?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var genderError by remember { mutableStateOf(false) }
    var dobError by remember { mutableStateOf(false) }
    var genderMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dobMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        }
    )
    val dobText = dobMillis?.let { formatDate(it) }

    // Dismisses the IME before opening a picker window. Creating a Popup/Dialog
    // window while the keyboard is animating is the crash trigger on compose-ui
    // < 1.7.1, so the window is opened only after the hide animation settles.
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    fun openPicker(onOpen: () -> Unit) {
        keyboardController?.hide()
        scope.launch {
            delay(250) // let the IME hide animation finish first
            onOpen()
        }
    }
    val scColors = LocalScColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Same subtle premium depth accents as the other auth screens.
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

        // Minimal "Skip" action pinned to the top-right corner: small text in
        // the app's accent color, 48dp touch target, equal top/right padding.
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .height(48.dp) // 48dp touch target; width wraps the small text
        ) {
            Text(
                "Skip",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            AuthBackButton(onClick = onBack, refined = true)
            Spacer(Modifier.height(8.dp))

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
                "Complete Your Profile",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "One last step before you get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            AuthTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (nameError && it.isNotBlank()) nameError = false
                },
                label = "Name",
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                isError = nameError,
                supportingText = if (nameError) "Please enter your name" else null
            )
            Spacer(Modifier.height(12.dp))

            Box {
                AuthPickerField(
                    label = "Gender",
                    value = gender,
                    onClick = { openPicker { genderMenuOpen = true } },
                    active = genderMenuOpen,
                    isError = genderError,
                    supportingText = if (genderError) "Please select your gender" else null,
                    iconContentDescription = "Select gender"
                )
                DropdownMenu(
                    expanded = genderMenuOpen,
                    onDismissRequest = { genderMenuOpen = false }
                ) {
                    GenderOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                gender = option.label
                                genderError = false
                                genderMenuOpen = false
                            },
                            trailingIcon = if (option.label == gender) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
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
            Spacer(Modifier.height(12.dp))

            AuthPickerField(
                label = "Date of Birth",
                value = dobText,
                onClick = { openPicker { showDatePicker = true } },
                active = showDatePicker,
                trailingIcon = Icons.Filled.CalendarMonth,
                isError = dobError,
                supportingText = if (dobError) "Please select your date of birth" else null,
                iconContentDescription = "Pick date of birth"
            )

            Spacer(Modifier.height(24.dp))

            AuthPrimaryButton(
                text = "Continue",
                onClick = {
                    val nameIsBlank = name.isBlank()
                    val genderMissing = gender == null
                    val dobMissing = dobMillis == null
                    nameError = nameIsBlank
                    genderError = genderMissing
                    dobError = dobMissing
                    if (!nameIsBlank && !genderMissing && !dobMissing) {
                        onContinue(name.trim(), gender!!, formatDate(dobMillis!!))
                    }
                }
            )

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
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            dobMillis = it
                            dobError = false
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        }
    }
}
