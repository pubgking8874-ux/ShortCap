package com.shortscap.app.favicon

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shortscap.app.theme.LocalScColors

/**
 * Reusable website favicon: loads the official logo for [domain] through
 * [FaviconRepository] (memory → disk → network, with caching), for ANY
 * domain — nothing is hardcoded. The globe placeholder doubles as the
 * loading state and the professional fallback when the favicon cannot be
 * downloaded or the network is unavailable, so the UI never breaks.
 *
 * Rendered at display density for crispness; the loaded logo sits on a soft
 * white tile (favicons are designed for light backgrounds) that stays
 * readable in both dark and light mode.
 */
@Composable
fun WebsiteFavicon(
    domain: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    corner: Dp = 10.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = LocalScColors.current

    // Keyed on the domain only: the cached bitmap is reused across sizes (the
    // app renders website tiles at one compact size), so no reload is needed.
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = domain.trim().lowercase()) {
        val targetPx = with(density) { size.roundToPx() }
        value = FaviconRepository.load(context, domain, targetPx)
    }

    val shape = RoundedCornerShape(corner)
    val favicon = bitmap
    if (favicon != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.94f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = favicon.asImageBitmap(),
                contentDescription = domain,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .padding(4.dp)
                    .clip(shape),
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(colors.CardHover, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Language,
                contentDescription = null,
                tint = colors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
