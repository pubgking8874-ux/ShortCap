package com.shortscap.app.screens.web

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScEntityIcon
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.model.ScEntity
import com.shortscap.app.model.ScEntityType
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScCursorBrush
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.web.WebRule
import com.shortscap.app.web.WebRuleStatus

/**
 * Shared components for the Web section screens (analytics root + Blocked /
 * Allowed rule screens). Everything reads from the theme and the active
 * language catalog — no hardcoded strings or colors.
 */

/** "45m" / "1h 30m" — localized website-usage duration formatting. */
fun formatWebDuration(minutes: Int, strings: AppStrings): String {
    if (minutes <= 0) return "0${strings.webMinutesShort}"
    if (minutes < 60) return "$minutes${strings.webMinutesShort}"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h${strings.webHoursShort}" else "$h${strings.webHoursShort} $m${strings.webMinutesShort}"
}

/**
 * Back bar for the Web sub-screens (Blocked / Allowed). Mirrors the app's
 * [com.shortscap.app.components.ScSubScreenTopBar] styling (38dp icon button,
 * radius 12, LogoText title) but WITHOUT status-bar padding — the Web tab
 * content already sits below the global app top bar.
 */
@Composable
fun WebSubScreenTopBar(title: String, onBack: () -> Unit) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            color = colors.TextPrimary,
            style = ScTextStyles.LogoText,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(38.dp))
    }
    ScDivider()
}

/** Compact rounded search field, matching the app's search-bar styling. */
@Composable
fun WebSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.Card, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(16.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = colors.TextPrimary, fontSize = 13.5.sp),
            cursorBrush = ScCursorBrush(),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(placeholder, color = colors.TextSecondary, fontSize = 13.5.sp)
                }
                inner()
            },
        )
    }
}

/**
 * One website-rule row: leading website tile (globe fallback), name + domain
 * and a single compact status action (Unblock / Block) — the only way to
 * change a rule's state. The rule itself is never deleted.
 */
@Composable
fun WebRuleRow(
    rule: WebRule,
    primaryLabel: String,
    primaryTint: Color,
    onPrimary: () -> Unit,
    statusLabel: String? = null,
) {
    val colors = LocalScColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScEntityIcon(
            entity = ScEntity(
                id = rule.id,
                title = rule.displayName,
                type = ScEntityType.WEBSITE,
                websiteUrl = rule.domain,
                fallbackColor = colors.TextSecondary,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(rule.displayName, color = colors.TextPrimary, style = ScTextStyles.BodySemiBold, maxLines = 1)
                if (statusLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.CardHover, RoundedCornerShape(999.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(statusLabel, color = colors.TextSecondary, style = ScTextStyles.Caption.copy(fontSize = 9.sp))
                    }
                }
            }
            Text(rule.domain, color = colors.TextSecondary, style = ScTextStyles.Caption, maxLines = 1)
        }
        val chipShape = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .clip(chipShape)
                .background(colors.CardHover, chipShape)
                .border(1.dp, colors.Divider, chipShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPrimary,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(primaryLabel, color = primaryTint, style = ScTextStyles.Caption)
        }
    }
}

private val domainRegex =
    Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$")

/**
 * Normalizes a typed URL/domain: lowercases and strips scheme, www and any
 * path — "https://WWW.Example.com/feed" -> "example.com".
 */
fun normalizeWebDomain(input: String): String {
    var d = input.trim().lowercase().removePrefix("https://").removePrefix("http://")
    while (d.startsWith("www.")) d = d.removePrefix("www.")
    return d.substringBefore("/").trim()
}

internal fun isValidWebDomain(domain: String): Boolean = domain.isNotBlank() && domainRegex.matches(domain)

/**
 * Compact clickable overview stat for the main Web screen (Blocked /
 * Allowed / Web Time). Tighter padding than the generic stat card so three
 * cards fit comfortably side by side on narrow screens; tapping navigates.
 */
@Composable
fun WebOverviewStat(
    iconKey: IconKey,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    val colors = LocalScColors.current
    val style = LocalIconStyle.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                IconTheme.icon(style, iconKey),
                contentDescription = null,
                tint = IconTheme.tint(style, iconKey, colors.Accent),
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            value,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = colors.TextSecondary,
            style = ScTextStyles.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sub != null) {
            Text(
                sub,
                color = colors.Success,
                style = ScTextStyles.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Status choice chip inside the Add Website dialog. */
@Composable
private fun StatusChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (active) colors.ChipActiveBg else colors.Card, shape)
            .border(1.dp, if (active) colors.Accent else colors.Divider, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (active) colors.ChipActiveText else colors.TextSecondary, style = ScTextStyles.Caption)
    }
}

/**
 * Add Website dialog — domain input with inline validation (format +
 * duplicate), a Block / Allow status choice (defaults to the screen's
 * status) and Cancel / Add actions.
 */
@Composable
fun AddWebsiteDialog(
    defaultStatus: WebRuleStatus,
    existingDomains: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (domain: String, status: WebRuleStatus) -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var domain by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(defaultStatus) }
    var error by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(22.dp)

    fun submit() {
        val d = normalizeWebDomain(domain)
        when {
            !isValidWebDomain(d) -> error = strings.webAddInvalid
            existingDomains.any { it.equals(d, ignoreCase = true) } -> error = strings.webAddDuplicate
            // On success the screen closes the dialog (single dismissal point).
            else -> onAdd(d, status)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.Divider, shape),
            shape = shape,
            color = colors.Card,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(strings.webAddDialogTitle, color = colors.TextPrimary, style = ScTextStyles.H1)
                Text(strings.webAddDialogDomainLabel, color = colors.TextSecondary, style = ScTextStyles.Label)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.CardHover, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(16.dp))
                    BasicTextField(
                        value = domain,
                        onValueChange = {
                            domain = it
                            error = null
                        },
                        textStyle = TextStyle(color = colors.TextPrimary, fontSize = 14.sp),
                        cursorBrush = ScCursorBrush(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (domain.isEmpty()) {
                                Text(strings.webAddDialogPlaceholder, color = colors.TextSecondary, fontSize = 13.5.sp)
                            }
                            inner()
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = strings.webBlockAction, active = status == WebRuleStatus.BLOCKED, onClick = { status = WebRuleStatus.BLOCKED })
                    StatusChip(label = strings.webAllow, active = status == WebRuleStatus.ALLOWED, onClick = { status = WebRuleStatus.ALLOWED })
                }
                error?.let {
                    Text(it, color = colors.Danger, style = ScTextStyles.Caption)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScButton(
                        label = strings.cancel,
                        variant = ScButtonVariant.SECONDARY,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ScButton(
                        label = strings.webAddDialogAdd,
                        variant = ScButtonVariant.PRIMARY,
                        onClick = ::submit,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

