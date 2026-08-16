package com.shortscap.app.screens.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.favicon.WebsiteFavicon
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.ShortVideoPlatform
import com.shortscap.app.shorts.InstalledShortApplicationRegistry
import com.shortscap.app.shorts.ShortApplicationEntry
import com.shortscap.app.shorts.ShortsControlEngine
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Short Applications — DYNAMIC installed-app discovery + the 24-hour lock
 * (Settings → Short Control → Short Applications).
 *
 * The list is NOT a hardcoded static UI list. Flow:
 *
 *   PackageManager → InstalledShortApplicationRegistry
 *   → ShortPlatformRegistry (supported platforms) → ShortApplicationEntry
 *
 * Only supported short-form platforms that are actually INSTALLED appear
 * (YouTube Shorts, Instagram Reels, TikTok, Snapchat Spotlight, Facebook
 * Reels, Moj, X, LinkedIn — whatever the existing platform registry knows).
 * Unknown/random installed apps never appear. Each row shows the app's REAL
 * label + icon from PackageManager (generic fallback when unreadable).
 *
 * 24-hour lock: while [ShortsControlEngine.hasActiveCycle] is true all
 * toggles are read-only (lock icon + locked switch, a locked banner at the
 * top) so the user cannot bypass enforcement mid-cycle — installing or
 * uninstalling an app is never a bypass mechanism. The list refreshes on
 * screen entry, on resume, and on package added/removed/replaced broadcasts
 * (no polling loop).
 */
@Composable
fun ShortsApplicationsScreen(
    platforms: List<ShortVideoPlatform>,
    onTogglePlatform: (String) -> Unit,
    onBack: () -> Unit,
    engine: ShortsControlEngine = ShortsControlEngine.shared,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    // Bump to re-discover: package broadcasts + lifecycle resume.
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_REPLACED,
                    -> refreshKey++
                    else -> Unit
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // The authoritative lock state — same active cycle as Shorts Limit / HUD.
    val locked = remember(engine) { engine.hasActiveCycle() }

    // Discover installed supported apps in deterministic registry order.
    val enabledById = remember(platforms) { platforms.associate { it.id to it.enabled } }
    val domainById = remember(platforms) { platforms.associate { it.id to it.domain } }
    val nameById = remember(platforms) { platforms.associate { it.id to it.name } }
    val entries = remember(context, platforms, locked, refreshKey) {
        InstalledShortApplicationRegistry.discover(context, enabledById, locked)
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.shortsApplications, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (locked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.Warning.copy(alpha = 0.10f))
                        .border(1.dp, colors.Warning.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(strings.shortsApplicationsLockedNotice, color = colors.Warning, style = ScTextStyles.BodySemiBold)
                }
            }

            if (entries.isEmpty()) {
                ScEmptyState(
                    title = strings.shortsApplicationsEmpty,
                    subtitle = strings.shortsApplicationsEmptyDesc,
                )
            } else {
                entries.forEach { entry ->
                    ShortApplicationRow(
                        entry = entry,
                        displayName = nameById[InstalledShortApplicationRegistry.platformId(entry.platform)]
                            ?: entry.platform.name,
                        domain = domainById[InstalledShortApplicationRegistry.platformId(entry.platform)],
                        locked = locked,
                        onToggle = {
                            if (!locked) {
                                onTogglePlatform(InstalledShortApplicationRegistry.platformId(entry.platform))
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * One discovered application row: real app icon + real label, the platform
 * descriptor, and an independent switch — or a LOCKED state (lock icon +
 * visibly disabled switch) while the 24-hour cycle is active. The row is
 * never hidden when locked; the app name, icon, installed + enabled states
 * stay visible.
 */
@Composable
private fun ShortApplicationRow(
    entry: ShortApplicationEntry,
    displayName: String,
    domain: String?,
    locked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        InstalledAppIcon(
            packageName = entry.packageName,
            fallbackDomain = domain,
            displayName = displayName,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.appLabel ?: displayName,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = displayName,
                color = colors.TextSecondary,
                style = ScTextStyles.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (locked) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = strings.shortsApplicationsLocked,
                tint = colors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            ScSwitch(
                on = entry.enabled,
                onToggle = {},
                modifier = Modifier.alpha(0.45f),
            )
        } else {
            ScSwitch(on = entry.enabled, onToggle = onToggle)
        }
    }
}

/**
 * The installed application's REAL icon via PackageManager (resolved at
 * display density, keyed per package). Falls back to the platform's brand
 * favicon, then to the generic letter/globe fallback — never crashes.
 */
@Composable
private fun InstalledAppIcon(
    packageName: String,
    fallbackDomain: String?,
    displayName: String,
    size: Dp = 44.dp,
    corner: Dp = 13.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val px = with(density) { size.roundToPx() }
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                info.loadIcon(context.packageManager).toBitmap(px, px).asImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = displayName,
            modifier = Modifier.size(size).clip(RoundedCornerShape(corner)),
        )
    } else if (fallbackDomain != null) {
        WebsiteFavicon(domain = fallbackDomain, size = size, corner = corner)
    } else {
        val colors = LocalScColors.current
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(colors.CardHover, RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.take(1),
                color = colors.TextSecondary,
                style = ScTextStyles.BodySemiBold,
                fontSize = 13.sp,
            )
        }
    }
}
