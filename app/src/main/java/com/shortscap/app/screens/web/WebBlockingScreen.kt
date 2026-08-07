package com.shortscap.app.screens.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScChip
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Website Blocking & Management — the MAIN Web screen (the Web tab root).
 *
 * Website blocking stays the primary feature: the URL input + Block Website
 * action sits at the top, followed by the Blocked / Allowed / Web Time
 * overview cards and the Blocked / Allowed / Recent navigation.
 *
 * The Web Time card is the ONLY entry point to the Web Usage Analytics
 * screen — analytics never replaces this page.
 *
 * Data flows from the ViewModel (WebRepository) — counts and today's usage
 * are derived, never hardcoded.
 */
@Composable
fun WebBlockingScreen(
    blockedCount: Int,
    allowedCount: Int,
    todayUsageMinutes: Int,
    onBlockWebsite: (String) -> Boolean,
    onOpenBlocked: () -> Unit,
    onOpenAllowed: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenAnalytics: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun block() {
        val d = normalizeWebDomain(url)
        when {
            !isValidWebDomain(d) -> error = strings.webAddInvalid
            else -> if (onBlockWebsite(d)) {
                url = ""
                error = null
            } else {
                error = strings.webAddDuplicate
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(strings.webBlockingTitle, color = colors.TextPrimary, style = ScTextStyles.H1)

        // ---- URL input + Block Website — the primary action ----
        ScCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.webEnterUrlLabel, color = colors.TextSecondary, style = ScTextStyles.Label)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.CardHover, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(16.dp))
                    BasicTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            error = null
                        },
                        textStyle = TextStyle(color = colors.TextPrimary, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (url.isEmpty()) {
                                Text(strings.webUrlPlaceholder, color = colors.TextSecondary, fontSize = 13.5.sp)
                            }
                            inner()
                        },
                    )
                }
                error?.let { Text(it, color = colors.Danger, style = ScTextStyles.Caption) }
                ScButton(
                    label = strings.webBlockWebsite,
                    variant = ScButtonVariant.PRIMARY,
                    icon = Icons.Filled.Block,
                    onClick = ::block,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Overview cards: Blocked / Allowed / Web Time (all tappable) ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WebOverviewStat(
                iconKey = IconKey.WEB_BLOCKED,
                label = strings.webBlocked,
                value = "$blockedCount",
                onClick = onOpenBlocked,
                modifier = Modifier.weight(1f),
            )
            WebOverviewStat(
                iconKey = IconKey.WEB_ALLOWED,
                label = strings.webAllowed,
                value = "$allowedCount",
                onClick = onOpenAllowed,
                modifier = Modifier.weight(1f),
            )
            WebOverviewStat(
                iconKey = IconKey.WEB_ANALYTICS,
                label = strings.webWebTime,
                value = formatWebDuration(todayUsageMinutes, strings),
                sub = strings.webPeriodToday,
                onClick = onOpenAnalytics,
                modifier = Modifier.weight(1f),
            )
        }

        // ---- Blocked / Allowed / Recent — dedicated screens ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScChip(label = strings.webBlocked, active = false, onClick = onOpenBlocked)
            ScChip(label = strings.webAllowed, active = false, onClick = onOpenAllowed)
            ScChip(label = strings.webRecent, active = false, onClick = onOpenRecent)
            Spacer(Modifier.weight(1f))
        }
    }
}
