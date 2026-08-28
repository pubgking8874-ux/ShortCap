package com.shortscap.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Shorts Insights — real Shorts usage summaries from Room
 * (Settings → Short Control → Shorts Insights).
 *
 * Shows the four period rows (Yesterday / Today / This Week / This Month)
 * with real counted-Short data from the existing shorts_usage table.
 */
@Composable
fun ShortsInsightsScreen(
    onBack: () -> Unit,
    todayDurationMillis: Long = 0L,
    todayCount: Int = 0,
    yesterdayDurationMillis: Long = 0L,
    yesterdayCount: Int = 0,
    weekDurationMillis: Long = 0L,
    weekCount: Int = 0,
    monthDurationMillis: Long = 0L,
    monthCount: Int = 0,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.fillMaxSize().background(colors.Bg)) {
        ScSubScreenTopBar(title = strings.shortsInsights, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle(strings.shortsInsights)

            PeriodRow(
                label = strings.shortsInsightsToday,
                durationMillis = todayDurationMillis,
                count = todayCount,
            )
            PeriodRow(
                label = strings.shortsInsightsYesterday,
                durationMillis = yesterdayDurationMillis,
                count = yesterdayCount,
            )
            PeriodRow(
                label = strings.shortsInsightsThisWeek,
                durationMillis = weekDurationMillis,
                count = weekCount,
            )
            PeriodRow(
                label = strings.shortsInsightsThisMonth,
                durationMillis = monthDurationMillis,
                count = monthCount,
            )
        }
    }
}

/**
 * One read-only period row — period label on the left, usage summary on the
 * right. Shows "—" when there are no records for the period.
 */
@Composable
private fun PeriodRow(
    label: String,
    durationMillis: Long,
    count: Int,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(22.dp)
    val valueText = if (count > 0) {
        val minutes = (durationMillis / 60_000L).toInt()
        val hours = minutes / 60
        val mins = minutes % 60
        val durationText = when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "<1m"
        }
        "$durationText \u00b7 $count Shorts"
    } else {
        "\u2014"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            color = if (count > 0) colors.TextPrimary else colors.TextSecondary,
            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
        )
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
