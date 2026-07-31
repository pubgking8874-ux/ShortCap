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
import com.shortscap.app.model.ScCircularMetric
import com.shortscap.app.model.ScEntity
import com.shortscap.app.model.ScEntityType
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScChrome
import com.shortscap.app.theme.ScInstagram
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.theme.ScWhatsApp

private val recentActivity = listOf(
    ScEntity(id = "instagram", title = "Instagram", type = ScEntityType.APP, packageName = "com.instagram.android", usageTime = "42m", timestamp = "10 min ago", fallbackColor = ScInstagram),
    ScEntity(id = "chrome", title = "Chrome", type = ScEntityType.APP, packageName = "com.android.chrome", usageTime = "28m", timestamp = "38 min ago", fallbackColor = ScChrome),
    ScEntity(id = "whatsapp", title = "WhatsApp", type = ScEntityType.APP, packageName = "com.whatsapp", usageTime = "15m", timestamp = "1h ago", fallbackColor = ScWhatsApp),
)

/** Mirrors function HomeScreen({ loading }) { ... } */
@Composable
fun HomeScreen(loading: Boolean, metrics: List<ScCircularMetric>) {
    val colors = LocalScColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Text("Good evening,", color = colors.TextSecondary, style = ScTextStyles.Label)
            Text("Arjun \uD83D\uDC4B", color = colors.TextPrimary, style = ScTextStyles.H1)
        }

        if (loading) {
            ScSkeleton(height = 290.dp)
        } else {
            ScCircularAnalyticsCarousel(metrics = metrics)
        }

        Column {
            Text("Quick Stats", color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
            Spacer(Modifier.height(12.dp))
            if (loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScSkeleton(height = 100.dp, modifier = Modifier.weight(1f))
                    ScSkeleton(height = 100.dp, modifier = Modifier.weight(1f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScStatCard(Icons.Filled.Smartphone, "Apps Used", "14", modifier = Modifier.weight(1f))
                    ScStatCard(Icons.Filled.Block, "Restricted Apps", "5", accent = colors.Danger, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScStatCard(Icons.Filled.Language, "Blocked Sites", "9", accent = colors.Warning, modifier = Modifier.weight(1f))
                    ScStatCard(Icons.Filled.Timer, "Focus Time", "1h 20m", sub = "+12% streak", accent = colors.Success, modifier = Modifier.weight(1f))
                }
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent Activity", color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
                Text("See all", color = colors.Accent, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            ScCard(modifier = Modifier.fillMaxWidth()) {
                recentActivity.forEachIndexed { index, item ->
                    ScEntityRow(
                        entity = item,
                        subtitle = item.timestamp,
                        trailing = {
                            Text(item.usageTime ?: "", color = colors.TextSecondary, fontSize = 13.sp)
                        },
                    )
                    if (index < recentActivity.size - 1) ScDivider()
                }
            }
        }
    }
}


