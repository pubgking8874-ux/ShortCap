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
 * Shorts Insights — read-only Shorts usage summaries
 * (Settings → Short Control → Shorts Insights).
 *
 * Shows the four period rows (Yesterday / Today / This Week / This Month).
 * Backend/reporting data is not connected yet, so the rows render an
 * explicit empty state (a "—" value + an explanatory card) instead of fake
 * numbers. No second reporting engine is created here: when the existing
 * backend sync/reporting layer provides Shorts aggregates, they plug in
 * behind this read-only shape without UI changes.
 */
@Composable
fun ShortsInsightsScreen(onBack: () -> Unit) {
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

            PeriodRow(label = strings.shortsInsightsToday)
            PeriodRow(label = strings.shortsInsightsYesterday)
            PeriodRow(label = strings.shortsInsightsThisWeek)
            PeriodRow(label = strings.shortsInsightsThisMonth)

            // Explicit empty state — no backend Shorts data connected yet.
            Text(
                text = strings.shortsInsightsEmpty,
                color = colors.TextSecondary,
                style = ScTextStyles.Body,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.Card, RoundedCornerShape(22.dp))
                    .border(1.dp, colors.Divider, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 20.dp),
            )
        }
    }
}

/**
 * One read-only period row — period label on the left, "—" placeholder on the
 * right until backend aggregates are available (never a fake number).
 */
@Composable
private fun PeriodRow(label: String) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(22.dp)
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
            text = "—",
            color = colors.TextSecondary,
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
