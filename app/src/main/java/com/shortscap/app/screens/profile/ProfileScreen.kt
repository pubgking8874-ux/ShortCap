package com.shortscap.app.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.model.ProfileData
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/** Formats a UTC millis timestamp as DD/MM/YYYY in the device's time zone. */
private fun formatDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val dd = date.dayOfMonth.toString().padStart(2, '0')
    val mm = date.monthValue.toString().padStart(2, '0')
    return "$dd/$mm/${date.year}"
}

/**
 * Parses a stored DD/MM/YYYY value back into UTC millis for the date picker,
 * so a profile loaded from the future backend pre-fills the picker correctly.
 */
private fun parseDate(dateText: String?): Long? {
    if (dateText.isNullOrBlank()) return null
    val parts = dateText.split("/")
    if (parts.size != 3) return null
    val dd = parts[0].toIntOrNull() ?: return null
    val mm = parts[1].toIntOrNull() ?: return null
    val yyyy = parts[2].toIntOrNull() ?: return null
    return try {
        LocalDate.of(yyyy, mm, dd).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

/**
 * Profile — dedicated profile editing screen opened from the Dashboard top
 * bar (replaces the old popup menu). Local-only today:
 *  - Load Profile       -> [ProfileData] passed in (seam: [ProfileRepository.loadProfile])
 *  - Update Profile     -> [onSave]          (seam: [ProfileRepository.updateProfile])
 *  - Update Picture     -> Android Photo Picker -> [onUpdatePicture]
 *
 * Future picture flow (prepared here, not implemented):
 * Tap profile picture -> Image Picker -> Crop (future) -> Upload to backend
 * ([ProfileRepository.uploadProfilePicture]) -> Update profile picture.
 *
 * If profile completion was skipped during onboarding, the fields are empty
 * here so the profile can be completed later. Back returns to the Dashboard
 * and never closes the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    profile: ProfileData,
    onSave: (fullName: String, gender: String?, dateOfBirth: String?) -> Unit,
    onUpdatePicture: (uri: String) -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val genderOptions = listOf(strings.profileMale, strings.profileFemale, strings.profilePreferNot)
    val context = LocalContext.current
    var fullName by remember { mutableStateOf(profile.fullName) }
    var gender by remember { mutableStateOf(profile.gender) }
    var dobText by remember { mutableStateOf(profile.dateOfBirth) }
    var genderMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Android system Photo Picker (images only). Future: a Camera/Gallery
    // chooser and a Crop step can slot in before [onUpdatePicture].
    // Note: picker URIs hold temporary read permission for the activity
    // lifetime — call takePersistableUriPermission if profile data is ever
    // persisted so the picture survives app restarts.
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onUpdatePicture(uri.toString())
    }
    val choosePicture: () -> Unit = {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    // Decode + EXIF-orientation-correct the stored picture (if any) off the
    // main thread. The pipeline (ProfilePictureLoader) never rotates sideways
    // images — the avatar always matches the Gallery. Future crop/compress/
    // upload steps live inside that loader, so the UI stays unchanged.
    LaunchedEffect(profile.pictureUri) {
        avatarBitmap = withContext(Dispatchers.IO) {
            profile.pictureUri
                ?.let { ProfilePictureLoader.load(context, it) }
                ?.asImageBitmap()
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = parseDate(profile.dateOfBirth),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        },
    )

    // Dismisses the IME before opening a picker window. Creating a Popup/Dialog
    // window while the keyboard is animating is a known crash trigger on
    // compose-ui < 1.7.1, so the window opens only after the hide settles.
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    fun openPicker(onOpen: () -> Unit) {
        keyboardController?.hide()
        scope.launch {
            delay(250) // let the IME hide animation finish first
            onOpen()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = strings.profileTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 24.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProfileAvatar(
                    picture = avatarBitmap,
                    onChoosePicture = choosePicture,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                strings.profileTapToChange,
                color = colors.TextDisabled,
                style = ScTextStyles.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            // Profile field icons go through the centralized icon system so
            // they follow the selected icon style app-wide.
            val style = LocalIconStyle.current
            ProfileField(
                label = strings.profileFullName,
                value = fullName,
                onValueChange = { fullName = it },
                placeholder = strings.profileNamePlaceholder,
                leadingIcon = IconTheme.icon(style, IconKey.PROFILE_PERSON),
                leadingIconTint = IconTheme.tint(style, IconKey.PROFILE_PERSON, colors.TextSecondary),
            )
            Spacer(Modifier.height(14.dp))

            ProfileField(
                label = strings.profileEmail,
                value = profile.email,
                onValueChange = {},
                placeholder = strings.profileEmailPlaceholder,
                readOnly = true,
                leadingIcon = IconTheme.icon(style, IconKey.PROFILE_EMAIL),
                leadingIconTint = IconTheme.tint(style, IconKey.PROFILE_EMAIL, colors.TextSecondary),
                trailingIcon = IconTheme.icon(style, IconKey.PROFILE_LOCK),
                trailingIconTint = IconTheme.tint(style, IconKey.PROFILE_LOCK, colors.TextSecondary),
                trailingIconContentDescription = strings.profileReadOnly,
            )
            Spacer(Modifier.height(14.dp))

            Box {
                ProfilePickerField(
                    label = strings.profileGender,
                    value = gender,
                    placeholder = strings.profileGenderPlaceholder,
                    onClick = { openPicker { genderMenuOpen = true } },
                )
                DropdownMenu(
                    expanded = genderMenuOpen,
                    onDismissRequest = { genderMenuOpen = false },
                ) {
                    genderOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(option, color = colors.TextPrimary, style = ScTextStyles.Body)
                            },
                            onClick = {
                                gender = option
                                genderMenuOpen = false
                            },
                            trailingIcon = if (option == gender) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = colors.Accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            ProfilePickerField(
                label = strings.profileDob,
                value = dobText,
                placeholder = strings.profileDobPlaceholder,
                onClick = { openPicker { showDatePicker = true } },
                trailingIcon = IconTheme.icon(style, IconKey.PROFILE_CALENDAR),
                trailingIconTint = IconTheme.tint(style, IconKey.PROFILE_CALENDAR, colors.TextSecondary),
                trailingIconContentDescription = strings.profileDob,
            )

            Spacer(Modifier.height(28.dp))

            ScButton(
                label = strings.profileSaveChanges,
                variant = ScButtonVariant.PRIMARY,
                enabled = fullName.isNotBlank(),
                onClick = { onSave(fullName, gender, dobText) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dobText = formatDate(it) }
                        showDatePicker = false
                    },
                ) {
                    Text(strings.ok)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(strings.cancel)
                }
            },
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

/**
 * Large circular profile picture with a floating Edit (pencil) button on its
 * top-right edge — the Instagram/Google/Discord style. Tapping either the
 * picture or the Edit button opens the same image picker. Press feedback:
 * soft ripple + small scale on the picture, scale + elevation on the button.
 */
@Composable
private fun ProfileAvatar(
    picture: ImageBitmap?,
    onChoosePicture: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    // ---- Avatar circle press state (ripple via default indication) ----
    val avatarSource = remember { MutableInteractionSource() }
    val avatarPressed by avatarSource.collectIsPressedAsState()
    val avatarBorder by animateColorAsState(
        targetValue = if (avatarPressed) colors.Accent.copy(alpha = 0.7f) else colors.Divider,
        label = "avatarBorder",
    )
    val avatarScale by animateFloatAsState(
        targetValue = if (avatarPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "avatarScale",
    )

    // ---- Floating Edit button press state ----
    val editSource = remember { MutableInteractionSource() }
    val editPressed by editSource.collectIsPressedAsState()
    val editScale by animateFloatAsState(
        targetValue = if (editPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "editButtonScale",
    )
    val editElevation by animateDpAsState(
        targetValue = if (editPressed) 8.dp else 4.dp,
        label = "editButtonElevation",
    )

    // Outer container is NOT clipped so the Edit button can float beyond the
    // circle edge; only the circle itself is clipped.
    Box(
        modifier = Modifier
            .size(112.dp)
            .graphicsLayer {
                scaleX = avatarScale
                scaleY = avatarScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.CardHover, CircleShape)
                .border(1.5.dp, avatarBorder, CircleShape)
                .clickable(
                    interactionSource = avatarSource,
                    indication = LocalIndication.current,
                    onClick = onChoosePicture,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (picture != null) {
                Image(
                    bitmap = picture,
                    contentDescription = "Profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = colors.TextSecondary,
                    modifier = Modifier.size(52.dp),
                )
            }
        }

        // Floating Edit button — overlaps the top-right edge, half on / half
        // off the circle, like modern apps. Accent fill, white pencil icon,
        // soft shadow, ring separator so it never sinks into the picture.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-10).dp)
                .size(38.dp)
                .graphicsLayer {
                    scaleX = editScale
                    scaleY = editScale
                }
                .shadow(
                    elevation = editElevation,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.35f),
                )
                .clip(CircleShape)
                .background(colors.Accent, CircleShape)
                .border(2.dp, colors.Bg, CircleShape)
                .clickable(
                    interactionSource = editSource,
                    indication = LocalIndication.current,
                    onClick = onChoosePicture,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = strings.profileChangePicture,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Dashboard-styled text field: label above, rounded filled box, optional icons. */
@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalScColors.current.TextSecondary,
    trailingIcon: ImageVector? = null,
    trailingIconTint: Color = LocalScColors.current.TextSecondary,
    trailingIconContentDescription: String? = null,
) {
    val colors = LocalScColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.CardHover, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = leadingIconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                textStyle = TextStyle(
                    color = if (readOnly) colors.TextDisabled else colors.TextPrimary,
                    fontSize = 14.sp,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = colors.TextDisabled, fontSize = 14.sp)
                        }
                        inner()
                    }
                },
            )
            if (trailingIcon != null) {
                Icon(
                    trailingIcon,
                    contentDescription = trailingIconContentDescription,
                    tint = trailingIconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Dashboard-styled picker field (Gender dropdown / Date of Birth). */
@Composable
private fun ProfilePickerField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector = Icons.Filled.ArrowDropDown,
    trailingIconTint: Color = LocalScColors.current.TextSecondary,
    trailingIconContentDescription: String? = null,
) {
    val colors = LocalScColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.CardHover, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                value ?: placeholder,
                color = if (value != null) colors.TextPrimary else colors.TextDisabled,
                style = TextStyle(fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(
                trailingIcon,
                contentDescription = trailingIconContentDescription,
                tint = trailingIconTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
