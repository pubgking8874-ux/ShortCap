package com.shortscap.app.screens.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScEntityIcon
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.favicon.WebsiteFavicon
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.ScEntity
import com.shortscap.app.model.ScEntityType
import com.shortscap.app.study.DefaultStudyAllowedApps
import com.shortscap.app.study.DefaultStudyAllowedWebsites
import com.shortscap.app.study.RestrictedStudyApps
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Allow Apps / Website (Study Mode) — the apps and websites that stay
 * accessible while a Study Mode session is active.
 *
 *  - Apps: the default study catalog PLUS any user-added apps, each with a
 *    REAL installed icon (via [ScEntityIcon] → PackageManager) + membership
 *    toggle. The header three-dot menu (⋮) opens \"Add App\" (a picker of
 *    installed apps — social-media / short-form packages are filtered out,
 *    frontend-only for now) and \"Manage Apps\" (remove any allowed app).
 *  - Websites: real favicons via the existing [WebsiteFavicon] system,
 *    membership toggles, and the styled add-website field. The \"Allow
 *    Websites\" header carries a small circular info (ⓘ) icon on the SAME line (far
 *    right) that explains what stays accessible during Study Mode.
 *
 * Membership lists live in StudyModeSettings — data, never hardcoded in the
 * UI — so a future backend can sync them without screen changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudyAllowedItemsScreen(
    allowedApps: List<String>,
    allowedWebsites: List<String>,
    onToggleApp: (String) -> Unit,
    onToggleWebsite: (String) -> Unit,
    onAddWebsite: (String) -> Boolean,
    onAddApp: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var websiteInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var inputFocused by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var addAppOpen by remember { mutableStateOf(false) }
    var manageAppsOpen by remember { mutableStateOf(false) }

    // Scroll container shared by the page and the Add Website field — when
    // the field is focused the IME pushes it off-screen, so we nudge the
    // page up (BringIntoView) until the field floats above the keyboard.
    val scrollState = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    fun bringInputIntoView() {
        scope.launch {
            delay(120) // let the IME inset land before measuring
            bringIntoViewRequester.bringIntoView()
        }
    }

    // Default catalog + any user-added domains, defaults first, deduplicated.
    val websiteItems = remember(allowedWebsites) {
        val defaults = DefaultStudyAllowedWebsites.map { it.id to it.name }
        val added = allowedWebsites
            .filter { d -> DefaultStudyAllowedWebsites.none { it.id == d } }
            .map { it to it }
        (defaults + added).distinctBy { it.first }
    }
    // App rows: the default study catalog (always shown, toggleable) plus any
    // user-added apps (present in the allowed list but not in the catalog).
    // Toggling an added app OFF removes it from the list — the same
    // membership behavior the custom websites use.
    val appItems = remember(allowedApps) {
        DefaultStudyAllowedApps.map { it.id to it.name } +
            allowedApps
                .filter { d -> DefaultStudyAllowedApps.none { it.id == d } }
                .map { it to it }
    }
    val inputShape = RoundedCornerShape(14.dp)

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(
            title = strings.studyAllowedItems,
            onBack = onBack,
            trailing = {
                AllowedAppsOverflowMenu(
                    onAddApp = { addAppOpen = true },
                    onManageApps = { manageAppsOpen = true },
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Apps ----
            SectionTitle(strings.studyAllowedApps)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                appItems.forEach { (id, name) ->
                    AllowedAppRow(
                        id = id,
                        name = name,
                        checked = id in allowedApps,
                        onToggle = { onToggleApp(id) },
                    )
                }
            }

            // ---- Allow Websites — the info (ⓘ) icon sits on the EXACT SAME
            //      line as the title, aligned to the far right. ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(strings.studyAllowedWebsites)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.CardHover)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { infoOpen = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = strings.studyAllowedInfoTitle,
                        tint = colors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                websiteItems.forEach { (domain, name) ->
                    AllowedWebsiteRow(
                        domain = domain,
                        name = name,
                        checked = domain in allowedWebsites,
                        onToggle = { onToggleWebsite(domain) },
                    )
                }
            }

            // ---- Add Website ----
            SectionTitle(strings.studyAllowedAddTitle)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Styled input — placeholder, focus border, error border, and
                // proper dark/light contrast (no default Android field look).
                // The whole field is registered with BringIntoViewRequester so
                // focusing it scrolls the page up above the IME (imePadding on
                // the scroll container reserves the keyboard space).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(inputShape)
                            .background(colors.CardHover, inputShape)
                            .border(
                                1.dp,
                                when {
                                    errorText != null -> colors.Danger
                                    inputFocused -> colors.Accent
                                    else -> colors.Divider
                                },
                                inputShape,
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = websiteInput,
                            onValueChange = {
                                websiteInput = it
                                errorText = null
                            },
                            textStyle = ScTextStyles.Body.copy(color = colors.TextPrimary),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.Accent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focused ->
                                    inputFocused = focused.isFocused
                                    if (focused.isFocused) bringInputIntoView()
                                },
                            decorationBox = { inner ->
                                if (websiteInput.isEmpty()) {
                                    Text(
                                        strings.studyAllowedWebsitePlaceholder,
                                        color = colors.TextDisabled,
                                        style = ScTextStyles.Body,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
                if (errorText != null) {
                    Text(errorText!!, color = colors.Danger, style = ScTextStyles.Caption)
                }
                ScButton(
                    label = strings.studyAllowedAdd,
                    icon = Icons.Filled.Add,
                    onClick = {
                        if (onAddWebsite(websiteInput)) {
                            websiteInput = ""
                            errorText = null
                        } else {
                            errorText = if (websiteInput.isBlank() || !websiteInput.contains(".")) {
                                strings.studyAllowedInvalid
                            } else {
                                strings.studyAllowedDuplicate
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // ---- Allow Websites info popup (info icon) — explains what stays
    //      accessible; never shown permanently on the screen. ----
    if (infoOpen) {
        AllowedInfoDialog(onDismiss = { infoOpen = false })
    }

    // ---- Add App picker (three-dot menu) — installed apps only; social /
    //      short-form packages are filtered out (frontend-only for now). ----
    if (addAppOpen) {
        AddAppDialog(
            allowedApps = allowedApps,
            onAdd = {
                onAddApp(it)
                addAppOpen = false
            },
            onDismiss = { addAppOpen = false },
        )
    }

    // ---- Manage Apps (three-dot menu) — remove any currently allowed app. ----
    if (manageAppsOpen) {
        ManageAppsDialog(
            allowedApps = allowedApps,
            onRemove = onRemoveApp,
            onDismiss = { manageAppsOpen = false },
        )
    }
}

/** One allowed app — REAL installed icon (brand-letter fallback) + toggle. */
@Composable
private fun AllowedAppRow(
    id: String,
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScEntityIcon(
            entity = ScEntity(
                id = id,
                title = name,
                type = ScEntityType.APP,
                packageName = id,
                fallbackColor = colors.Accent,
            ),
            size = 38.dp,
            corner = 11.dp,
        )
        Text(
            name,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ScSwitch(on = checked, onToggle = onToggle)
    }
}

/** One allowed website — real favicon (globe fallback) + name/domain + toggle. */
@Composable
private fun AllowedWebsiteRow(
    domain: String,
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WebsiteFavicon(domain = domain, size = 38.dp, corner = 11.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                domain,
                color = colors.TextSecondary,
                style = ScTextStyles.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ScSwitch(on = checked, onToggle = onToggle)
    }
}

/**
 * Header three-dot (⋮) menu on the far right of the \"Allow Apps / Website\"
 * title bar — Add App (installed-app picker) and Manage Apps (remove
 * allowed apps). Clean, minimal, using the existing popup design.
 */
@Composable
private fun AllowedAppsOverflowMenu(
    onAddApp: () -> Unit,
    onManageApps: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = null,
                tint = colors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        // Floating-menu treatment: inset slightly from the screen edge (never
        // glued to it), rounded on ALL four corners, card background + border
        // + soft shadow. Rows keep icon and label tightly grouped.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(x = (-12).dp, y = 4.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.Card,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, colors.Divider),
        ) {
            Column {
                OverflowActionRow(
                    icon = Icons.Filled.Add,
                    iconTint = colors.Accent,
                    label = strings.studyAllowedMenuAddApp,
                    onClick = { open = false; onAddApp() },
                )
                OverflowActionRow(
                    icon = Icons.Filled.Close,
                    iconTint = colors.Danger,
                    label = strings.studyAllowedMenuManageApps,
                    onClick = { open = false; onManageApps() },
                )
            }
        }
    }
}

/** One floating-menu row — icon and label tightly grouped (no wide gap). */
@Composable
private fun OverflowActionRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Text(label, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
    }
}

/**
 * Add App picker — every installed, launchable app (real icon + label),
 * EXCLUDING the restricted social-media / short-form packages (frontend
 * filter only; backend enforcement is a later task) and anything already in
 * the catalog or allowed list.
 */
@Composable
private fun AddAppDialog(
    allowedApps: List<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    // The installed-app scan issues a PackageManager binder call per package,
    // so it runs OFF the main thread; the dialog shows a brief loading state.
    var installed by remember { mutableStateOf<List<InstalledApp>?>(null) }
    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.Default) {
            installedLaunchableApps(context.applicationContext)
        }
    }
    val shown = remember(allowedApps, installed) {
        val apps = installed ?: return@remember emptyList()
        apps
            .filter { it.packageName !in RestrictedStudyApps }
            .filter { it.packageName !in allowedApps }
            .filter { app -> DefaultStudyAllowedApps.none { it.id == app.packageName } }
            .sortedBy { it.label.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextSecondary,
        title = { Text(strings.studyAllowedMenuAddApp) },
        text = {
            when {
                installed == null -> Text(strings.loading, color = colors.TextSecondary, style = ScTextStyles.Body)
                shown.isEmpty() -> Text(strings.studyAllowedPickerEmpty, color = colors.TextSecondary, style = ScTextStyles.Body)
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        shown.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onAdd(app.packageName) },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ScEntityIcon(
                                    entity = ScEntity(
                                        id = app.packageName,
                                        title = app.label,
                                        type = ScEntityType.APP,
                                        packageName = app.packageName,
                                        fallbackColor = colors.Accent,
                                    ),
                                    size = 34.dp,
                                    corner = 10.dp,
                                )
                                Text(
                                    app.label,
                                    color = colors.TextPrimary,
                                    style = ScTextStyles.BodySemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = colors.TextSecondary)
            }
        },
    )
}

/**
 * Manage Apps — every currently allowed app (catalog + added) with a remove
 * action. Removing a catalog app just flips its membership OFF (the row
 * stays visible on the screen); removing an added app drops it from the list.
 */
@Composable
private fun ManageAppsDialog(
    allowedApps: List<String>,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val items = remember(allowedApps) {
        DefaultStudyAllowedApps.filter { it.id in allowedApps }.map { it.id to it.name } +
            allowedApps
                .filter { d -> DefaultStudyAllowedApps.none { it.id == d } }
                .map { it to it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextSecondary,
        title = { Text(strings.studyAllowedMenuManageApps) },
        text = {
            if (items.isEmpty()) {
                Text(strings.studyAllowedManageEmpty, color = colors.TextSecondary, style = ScTextStyles.Body)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items.forEach { (id, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ScEntityIcon(
                                entity = ScEntity(
                                    id = id,
                                    title = name,
                                    type = ScEntityType.APP,
                                    packageName = id,
                                    fallbackColor = colors.Accent,
                                ),
                                size = 34.dp,
                                corner = 10.dp,
                            )
                            Text(
                                name,
                                color = colors.TextPrimary,
                                style = ScTextStyles.BodySemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemove(id) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = strings.webRemove,
                                    tint = colors.Danger,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = colors.TextSecondary)
            }
        },
    )
}

/** Small info popup opened by the info (ⓘ) icon next to \"Allow Websites\". */
@Composable
private fun AllowedInfoDialog(onDismiss: () -> Unit) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextSecondary,
        title = { Text(strings.studyAllowedInfoTitle) },
        text = {
            Text(
                strings.studyAllowedInfoDesc,
                color = colors.TextPrimary,
                style = ScTextStyles.Body,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.ok, color = colors.Accent)
            }
        },
    )
}

/** One installed, launchable app (package + friendly label). */
private data class InstalledApp(val packageName: String, val label: String)

/** Every installed app the user can actually open, with friendly labels. */
private fun installedLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val installed = if (Build.VERSION.SDK_INT >= 33) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0)
    }
    return installed.mapNotNull { info ->
        // Only apps the user can actually launch belong in the picker.
        if (pm.getLaunchIntentForPackage(info.packageName) == null) return@mapNotNull null
        val label = pm.getApplicationLabel(info).toString().ifBlank { info.packageName }
        InstalledApp(info.packageName, label)
    }
}

/** Uppercased section heading, matching the app's section-title style. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = LocalScColors.current.TextSecondary,
        style = ScTextStyles.SectionTitle,
    )
}
