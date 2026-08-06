package com.shortscap.app.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScCircularAnalyticsCarousel
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScEntityRow
import com.shortscap.app.components.ScSkeleton
import com.shortscap.app.components.ScStatCard
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.ScCircularMetric
import com.shortscap.app.model.ScEntity
import com.shortscap.app.model.ScEntityType
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScChrome
import com.shortscap.app.theme.ScInstagram
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.theme.ScWhatsApp

// Timestamps are mock data; they follow the language catalog so the Recent
// Activity rows never show a stale language.
private fun recentActivity(strings: com.shortscap.app.i18n.AppStrings) = listOf(
    ScEntity(id = "instagram", title = "Instagram", type = ScEntityType.APP, packageName = "com.instagram.android", usageTime = "42m", timestamp = strings.homeRecentTime1, fallbackColor = ScInstagram),
    ScEntity(id = "chrome", title = "Chrome", type = ScEntityType.APP, packageName = "com.android.chrome", usageTime = "28m", timestamp = strings.homeRecentTime2, fallbackColor = ScChrome),
    ScEntity(id = "whatsapp", title = "WhatsApp", type = ScEntityType.APP, packageName = "com.whatsapp", usageTime = "15m", timestamp = strings.homeRecentTime3, fallbackColor = ScWhatsApp),
)

/** Mirrors function HomeScreen({ loading }) { ... } */
@Composable
fun HomeScreen(loading: Boolean, metrics: List<ScCircularMetric>) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Text(strings.homeGreeting, color = colors.TextSecondary, style = ScTextStyles.Label)
            Text("Arjun \uD83D\uDC4B", color = colors.TextPrimary, style = ScTextStyles.H1)
        }

        if (loading) {
            ScSkeleton(height = 290.dp)
        } else {
            ScCircularAnalyticsCarousel(metrics = metrics)
        }

        Column {
            Text(strings.homeQuickStats, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
            Spacer(Modifier.height(12.dp))
            if (loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScSkeleton(height = 100.dp, modifier = Modifier.weight(1f))
                    ScSkeleton(height = 100.dp, modifier = Modifier.weight(1f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScStatCard(Icons.Filled.Smartphone, strings.homeAppsUsed, "14", modifier = Modifier.weight(1f))
                    ScStatCard(Icons.Filled.Block, strings.homeRestrictedApps, "5", accent = colors.Danger, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScStatCard(Icons.Filled.Language, strings.homeBlockedSites, "9", accent = colors.Warning, modifier = Modifier.weight(1f))
                    ScStatCard(Icons.Filled.Timer, strings.homeFocusTime, "1h 20m", sub = strings.homeFocusStreak, accent = colors.Success, modifier = Modifier.weight(1f))
                }
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.homeRecentActivity, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
                Text(strings.homeSeeAll, color = colors.Accent, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            ScCard(modifier = Modifier.fillMaxWidth()) {
                val items = recentActivity(strings)
                items.forEachIndexed { index, item ->
                    ScEntityRow(
                        entity = item,
                        subtitle = item.timestamp,
                        trailing = {
                            Text(item.usageTime ?: "", color = colors.TextSecondary, fontSize = 13.sp)
                        },
                    )
                    if (index < items.size - 1) ScDivider()
                }
            }
        }
    }
}


