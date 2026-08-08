package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.favicon.WebsiteFavicon
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.ShortVideoPlatform
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Shorts Control — the dedicated screen for per-platform Shorts monitoring
 * (opened from Monitoring → Shorts Control).
 *
 * Each supported short-video platform (YouTube Shorts, Instagram Reels,
 * Facebook Reels, Snapchat Spotlight) is listed separately with its OWN
 * brand icon — resolved automatically by the centralized favicon system from
 * the platform's real domain ([ShortVideoPlatform.domain]), never a generic
 * icon — and its OWN independent on/off switch.
 *
 * The platform list and every enabled state come from the data model
 * ([MonitoringSettings.platforms]), never from the UI, so a future backend
 * can synchronize per-platform Shorts Monitoring settings behind the same
 * shape without any screen changes. Shorts Monitoring stays separate from
 * Device Monitoring: this screen only ever touches the platforms list.
 */
@Composable
fun ShortsControlScreen(
    platforms: List<ShortVideoPlatform>,
    onTogglePlatform: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = LocalAppStrings.current.monitoringShortsControl, onBack = onBack)

        // Clean options page (per the Settings design rule — no intro card):
        // only the per-platform toggles, each with its real brand icon.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            platforms.forEach { platform ->
                PlatformRow(
                    platform = platform,
                    onToggle = { onTogglePlatform(platform.id) },
                )
            }
        }
    }
}

/**
 * One platform row — real brand favicon tile + platform name + independent
 * switch, matching the ShortsCap premium card proportions (44dp tile, 13dp
 * corner, tight padding). The whole row toggles the platform, same as the
 * switch itself.
 */
@Composable
private fun PlatformRow(
    platform: ShortVideoPlatform,
    onToggle: () -> Unit,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WebsiteFavicon(domain = platform.domain, size = 44.dp, corner = 13.dp)
        Text(
            text = platform.name,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ScSwitch(on = platform.enabled, onToggle = onToggle)
    }
}
