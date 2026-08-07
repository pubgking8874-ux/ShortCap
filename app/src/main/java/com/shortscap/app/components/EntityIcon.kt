package com.shortscap.app.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.shortscap.app.favicon.WebsiteFavicon
import com.shortscap.app.model.ScEntity
import com.shortscap.app.model.ScEntityType
import com.shortscap.app.theme.LocalScColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Leading icon for any app/website [ScEntity]. Resolution order:
 *
 * 1. Websites with a URL — the official website favicon via the centralized
 *    [WebsiteFavicon] system (memory → disk → network cache; globe fallback
 *    doubles as the loading state). Works automatically for ANY domain.
 * 2. Backend-supplied icon reference ([ScEntity.icon]) — decoded later when
 *    the API/Accessibility data source is connected.
 * 3. Installed application icon via PackageManager (apps with a packageName).
 * 4. Clean fallback — a brand-letter tile (apps) or a globe (websites).
 *
 * Icons are resolved asynchronously and keyed per entity, so nothing recomposes
 * unnecessarily and empty space never appears where an icon should be.
 */
@Composable
fun ScEntityIcon(
    entity: ScEntity,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    corner: Dp = 10.dp,
) {
    // Websites: the favicon system owns the leading icon (loading state +
    // fallback included), so every website list app-wide picks it up. This
    // branch takes precedence over the future [entity.icon] reference — for
    // websites the official favicon is always resolved by domain.
    if (entity.type == ScEntityType.WEBSITE && entity.websiteUrl != null) {
        WebsiteFavicon(domain = entity.websiteUrl, modifier = modifier, size = size, corner = corner)
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val loaded by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = entity.icon,
        key2 = entity.packageName,
        key3 = entity.type,
    ) {
        // Resolve at the display density so installed icons stay crisp on any screen.
        val sizePx = with(density) { size.roundToPx() }
        value = when {
            entity.icon != null -> null // Future: decode the icon reference from backend/Accessibility data
            entity.type == ScEntityType.APP && entity.packageName != null ->
                withContext(Dispatchers.IO) { loadInstalledAppIcon(context, entity.packageName, sizePx) }
            else -> null
        }
    }

    val icon = loaded
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = entity.title,
            modifier = modifier.size(size).clip(RoundedCornerShape(corner)),
        )
    } else {
        ScIconFallback(entity = entity, modifier = modifier, size = size, corner = corner)
    }
}

/** Returns the installed app icon by package name, or null when not installed. */
private fun loadInstalledAppIcon(context: Context, packageName: String, sizePx: Int): ImageBitmap? = runCatching {
    val pm = context.packageManager
    val info = pm.getApplicationInfo(packageName, 0)
    info.loadIcon(pm).toBitmap(sizePx, sizePx).asImageBitmap()
}.getOrNull()

/** Clean placeholder/fallback: brand-letter tile for apps, globe for websites. */
@Composable
private fun ScIconFallback(
    entity: ScEntity,
    modifier: Modifier,
    size: Dp,
    corner: Dp,
) {
    val colors = LocalScColors.current
    val isWebsite = entity.type == ScEntityType.WEBSITE
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(
                if (isWebsite) colors.CardHover else entity.fallbackColor.copy(alpha = 0.13f),
                RoundedCornerShape(corner),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isWebsite) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(16.dp))
        } else {
            Text(entity.title.take(1), color = entity.fallbackColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
